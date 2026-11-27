package com.luoty.agent.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 会话仓库：把多会话 / 多轮消息保存在应用私有目录的 JSON 文件里。
 * 内存里用 StateFlow 暴露，UI 直接订阅；写盘做了防抖与原子替换。
 */
class SessionRepository(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "sessions.json")
    private val backup = File(appContext.filesDir, "sessions.bak.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private var saveJob: Job? = null

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    init {
        _sessions.value = readFromDisk()
            .map { session ->
                // 进程被杀时中断的流式消息，恢复时标记为已停止，避免留下"永远转圈"
                session.copy(
                    running = false,
                    messages = session.messages.map { message ->
                        if (message.status == MessageStatus.STREAMING) {
                            message.copy(status = MessageStatus.CANCELLED)
                        } else {
                            message
                        }
                    },
                )
            }
            .sortedByDescending { it.updatedAt }
    }

    // ---------------------------------------------------------------- 读取

    private fun readFromDisk(): List<Session> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("sessions") ?: JSONArray()
            (0 until arr.length()).map { Session.fromJson(arr.getJSONObject(it)) }
        }.getOrElse {
            // 主文件坏了就尝试备份，再不行就当空仓库
            runCatching {
                val root = JSONObject(backup.readText())
                val arr = root.optJSONArray("sessions") ?: JSONArray()
                (0 until arr.length()).map { Session.fromJson(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }
    }

    // ---------------------------------------------------------------- 写盘

    fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(500)
            saveNow()
        }
    }

    suspend fun flush() {
        saveJob?.cancel()
        saveNow()
    }

    private suspend fun saveNow() = writeMutex.withLock {
        runCatching {
            val arr = JSONArray(_sessions.value.map { it.toJson() })
            val root = JSONObject().put("version", 1).put("sessions", arr)
            val text = root.toString()
            val tmp = File(appContext.filesDir, "sessions.json.tmp")
            tmp.writeText(text)
            if (file.exists()) {
                runCatching { file.copyTo(backup, overwrite = true) }
            }
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }
    }

    // ---------------------------------------------------------------- 会话操作

    fun createSession(title: String = "新会话"): Session {
        val now = System.currentTimeMillis()
        val session = Session(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
        )
        _sessions.update { list -> listOf(session) + list }
        scheduleSave()
        return session
    }

    fun deleteSession(id: String) {
        _sessions.update { list -> list.filterNot { it.id == id } }
        scheduleSave()
    }

    fun clearAll() {
        _sessions.value = emptyList()
        scheduleSave()
    }

    fun renameSession(id: String, title: String) {
        val clean = title.trim().replace(Regex("\\s+"), " ").take(60)
        if (clean.isEmpty()) return
        updateSession(id) { it.copy(title = clean, updatedAt = System.currentTimeMillis()) }
    }

    /** 第一条用户消息自动生成会话标题 */
    fun autoTitleIfNeeded(sessionId: String, text: String) {
        val session = find(sessionId) ?: return
        if (session.title != "新会话" && session.title.isNotBlank()) return
        val title = text.trim().lineSequence().firstOrNull { it.isNotBlank() }?.take(28) ?: return
        if (title.isNotBlank()) {
            updateSession(sessionId) { it.copy(title = title) }
        }
    }

    fun updateSession(id: String, transform: (Session) -> Session) {
        _sessions.update { list ->
            list.map { if (it.id == id) transform(it) else it }
        }
        scheduleSave()
    }

    fun updateMessage(sessionId: String, messageId: String, transform: (ChatMessage) -> ChatMessage) {
        updateSession(sessionId) { session ->
            session.copy(
                messages = session.messages.map { if (it.id == messageId) transform(it) else it },
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun appendMessage(sessionId: String, message: ChatMessage) {
        updateSession(sessionId) { session ->
            session.copy(messages = session.messages + message, updatedAt = System.currentTimeMillis())
        }
    }

    /** 删除 index 及其之后的消息（重新生成时用） */
    fun truncateMessages(sessionId: String, index: Int) {
        updateSession(sessionId) { session ->
            if (index >= session.messages.size) return@updateSession session
            session.copy(
                messages = session.messages.take(index),
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun setRunning(sessionId: String, running: Boolean) {
        updateSession(sessionId) { it.copy(running = running) }
    }

    fun setGoal(sessionId: String, goal: String) {
        updateSession(sessionId) {
            it.copy(
                goal = goal.trim().take(2000),
                goalEnabled = goal.isNotBlank(),
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun setGoalEnabled(sessionId: String, enabled: Boolean) {
        updateSession(sessionId) { it.copy(goalEnabled = enabled) }
    }

    fun setPlanMode(sessionId: String, enabled: Boolean) {
        updateSession(sessionId) { it.copy(planMode = enabled) }
    }

    fun setCompacting(sessionId: String, compacting: Boolean) {
        updateSession(sessionId) { it.copy(compacting = compacting) }
    }

    fun setTasks(sessionId: String, tasks: List<TaskItem>) {
        updateSession(sessionId) { it.copy(tasks = tasks) }
    }

    fun setActiveServer(sessionId: String, serverId: String) {
        updateSession(sessionId) { it.copy(activeServerId = serverId) }
    }

    fun enqueue(sessionId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        updateSession(sessionId) { it.copy(queue = it.queue + QueuedMessage(text = clean)) }
    }

    fun updateQueueItem(sessionId: String, id: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        updateSession(sessionId) { session ->
            session.copy(queue = session.queue.map { if (it.id == id) it.copy(text = clean) else it })
        }
    }

    fun removeQueueItem(sessionId: String, id: String) {
        updateSession(sessionId) { session ->
            session.copy(queue = session.queue.filterNot { it.id == id })
        }
    }

    fun promoteQueueItem(sessionId: String, id: String) {
        updateSession(sessionId) { session ->
            val item = session.queue.firstOrNull { it.id == id } ?: return@updateSession session
            session.copy(queue = listOf(item) + session.queue.filterNot { it.id == id })
        }
    }

    fun popQueue(sessionId: String): QueuedMessage? {
        val session = find(sessionId) ?: return null
        val first = session.queue.firstOrNull() ?: return null
        updateSession(sessionId) { it.copy(queue = it.queue.drop(1)) }
        return first
    }

    fun clearQueue(sessionId: String) {
        updateSession(sessionId) { it.copy(queue = emptyList()) }
    }

    /** 把早期消息折叠成一段摘要，只保留最近几条 */
    fun applyCompaction(sessionId: String, summary: String, keepRecent: Int, summarizedCount: Int) {
        updateSession(sessionId) { session ->
            session.copy(
                summary = summary,
                summaryCount = session.summaryCount + summarizedCount,
                messages = session.messages.takeLast(keepRecent),
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun find(id: String): Session? = _sessions.value.firstOrNull { it.id == id }
}
