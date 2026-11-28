package com.luoty.agent.agent

import com.luoty.agent.data.AppSettings
import com.luoty.agent.data.ChatMessage
import com.luoty.agent.data.MessageStatus
import com.luoty.agent.data.PERM_CONFIRM
import com.luoty.agent.data.PERM_SAFE
import com.luoty.agent.data.PERM_READONLY
import com.luoty.agent.data.Role
import com.luoty.agent.data.Session
import com.luoty.agent.data.SessionRepository
import com.luoty.agent.data.ToolInvocation
import com.luoty.agent.data.ToolStatus
import com.luoty.agent.data.WRITE_TOOLS
import com.luoty.agent.net.ApiException
import com.luoty.agent.net.ApiMessage
import com.luoty.agent.net.ApiToolCall
import com.luoty.agent.net.DeepSeekClient
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 需要用户点「允许 / 拒绝」的工具调用 */
data class ApprovalRequest(
    val id: String,
    val sessionId: String,
    val messageId: String,
    val call: ToolInvocation,
)

/** 模型拿不准、需要用户点选的问题（显示在输入框上方） */
data class UserQuestion(
    val id: String,
    val sessionId: String,
    val messageId: String,
    val question: String,
    val options: List<String>,
)

/**
 * Agent 主循环：请求模型 → 执行工具 → 把结果喂回 → 再请求，直到模型给出最终回答。
 * 每个会话一条独立协程，所以多个会话可以同时跑（手机上也能并行处理多个任务）。
 * 支持权限预设、逐次确认、计划模式、上下文自动压缩。
 */
class AgentEngine(
    private val repo: SessionRepository,
    private val client: DeepSeekClient,
    private val tools: ToolRegistry,
    private val attachmentStore: com.luoty.agent.data.AttachmentStore,
    private val serverStore: com.luoty.agent.data.ServerStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val waiters = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private val _approvals = MutableStateFlow<List<ApprovalRequest>>(emptyList())
    val approvals: StateFlow<List<ApprovalRequest>> = _approvals.asStateFlow()

    private val _questions = MutableStateFlow<List<UserQuestion>>(emptyList())
    val questions: StateFlow<List<UserQuestion>> = _questions.asStateFlow()
    private val questionWaiters = ConcurrentHashMap<String, CompletableDeferred<String?>>()

    fun isRunning(sessionId: String): Boolean = jobs[sessionId]?.isActive == true

    fun send(
        sessionId: String,
        text: String,
        settings: AppSettings,
        attachments: List<com.luoty.agent.data.Attachment> = emptyList(),
    ) {
        if (isRunning(sessionId)) return
        val clean = text.trim()
        if (clean.isEmpty() && attachments.isEmpty()) return
        repo.appendMessage(
            sessionId,
            ChatMessage(role = Role.USER, content = clean, attachments = attachments),
        )
        if (settings.autoTitle && clean.isNotEmpty()) repo.autoTitleIfNeeded(sessionId, clean)
        startJob(sessionId, settings)
    }

    /** 删掉最后一条回答后重新请求（用同一段用户消息） */
    fun regenerate(sessionId: String, settings: AppSettings) {
        if (isRunning(sessionId)) return
        val session = repo.find(sessionId) ?: return
        val lastUserIndex = session.messages.indexOfLast { it.role == Role.USER }
        if (lastUserIndex < 0) return
        repo.truncateMessages(sessionId, lastUserIndex + 1)
        startJob(sessionId, settings)
    }

    fun stop(sessionId: String) {
        jobs[sessionId]?.cancel()
    }

    fun stopAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    /** UI 点「允许 / 拒绝」 */
    fun approve(requestId: String, allow: Boolean) {
        waiters[requestId]?.complete(allow)
    }

    /** UI 点选某个选项（null = 跳过，让模型自己决定） */
    fun answerQuestion(questionId: String, answer: String?) {
        questionWaiters[questionId]?.complete(answer)
    }

    private suspend fun askUser(
        sessionId: String,
        messageId: String,
        call: ToolInvocation,
        question: String,
        options: List<String>,
    ): String? {
        val request = UserQuestion(UUID.randomUUID().toString(), sessionId, messageId, question, options)
        val deferred = CompletableDeferred<String?>()
        questionWaiters[request.id] = deferred
        _questions.update { it + request }
        return try {
            withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { deferred.await() }
        } finally {
            questionWaiters.remove(request.id)
            _questions.update { list -> list.filterNot { it.id == request.id } }
        }
    }

    // ------------------------------------------------------------ 上下文压缩

    fun compactNow(sessionId: String, settings: AppSettings) {
        if (isRunning(sessionId)) return
        if (repo.find(sessionId)?.compacting == true) return
        scope.launch { compact(sessionId, settings) }
    }

    private suspend fun maybeCompact(sessionId: String, settings: AppSettings) {
        if (!settings.autoCompact) return
        val session = repo.find(sessionId) ?: return
        if (session.compacting) return
        val threshold = minOf(
            settings.compactThreshold,
            (settings.contextWindow * 3 * 80 / 100),
        )
        if (estimateContextChars(session) < threshold) return
        compact(sessionId, settings)
    }

    private suspend fun compact(sessionId: String, settings: AppSettings): Boolean {
        val session = repo.find(sessionId) ?: return false
        val keep = settings.compactKeepRecent.coerceIn(2, 20)
        if (session.messages.size <= keep) return false
        val older = session.messages.dropLast(keep)
        repo.setCompacting(sessionId, true)
        val summary = try {
            val prompt = listOf(
                ApiMessage(
                    "system",
                    "你是上下文压缩器。把给定的编码助手对话压缩成简洁的中文摘要，保留：" +
                        "用户目标、已完成的修改与文件路径、关键决策、未完成事项、重要错误。" +
                        "只输出摘要正文，不要客套话。",
                ),
                ApiMessage("user", transcript(older).take(60_000)),
            )
            client.complete(settings, prompt, maxTokens = 1000).trim()
        } catch (e: CancellationException) {
            repo.setCompacting(sessionId, false)
            throw e
        } catch (e: Exception) {
            ""
        }
        val text = summary.ifBlank { fallbackSummary(older) }
        repo.applyCompaction(sessionId, text, keep, older.size)
        repo.setCompacting(sessionId, false)
        return summary.isNotBlank()
    }

    private fun estimateContextChars(session: Session): Int {
        var total = session.summary.length + 600
        for (message in session.messages) {
            total += message.content.length + message.reasoning.length
            for (call in message.toolCalls) {
                total += call.arguments.length + (call.result?.length ?: 0)
            }
        }
        return total
    }

    private fun transcript(messages: List<ChatMessage>): String = buildString {
        for (message in messages) {
            when (message.role) {
                Role.USER -> append("用户：").append(message.content.take(2000)).append("\n\n")
                Role.ASSISTANT -> {
                    if (message.content.isNotBlank()) {
                        append("助手：").append(message.content.take(1200)).append('\n')
                    }
                    for (call in message.toolCalls) {
                        append("  [工具 ").append(call.name).append("] ")
                        append(call.arguments.take(300))
                        call.result?.let { append(" → ").append(it.take(500)) }
                        append('\n')
                    }
                    append('\n')
                }
            }
        }
    }

    private fun fallbackSummary(older: List<ChatMessage>): String {
        val lastTopic = older.lastOrNull { it.role == Role.USER }?.content?.take(300).orEmpty()
        return "（早期 ${older.size} 条消息已压缩，摘要生成失败）最后话题：$lastTopic"
    }

    // ------------------------------------------------------------ 主循环

    private fun startJob(sessionId: String, settings: AppSettings) {
        val job = scope.launch {
            repo.setRunning(sessionId, true)
            try {
                runLoop(sessionId, settings)
            } catch (_: CancellationException) {
                // runLoop 内部已把占位消息标成"已停止"
            } catch (e: Exception) {
                markLastStreamingError(sessionId, e.message ?: "未知错误")
            } finally {
                repo.setRunning(sessionId, false)
                jobs.remove(sessionId, coroutineContext[Job])
            }
        }
        jobs[sessionId] = job
        if (job.isCompleted) jobs.remove(sessionId, job)
    }

    private suspend fun runLoop(sessionId: String, settings: AppSettings) {
        val maxSteps = settings.maxSteps.coerceIn(1, 60)
        var step = 0

        while (true) {
            step++
            if (settings.autoCompact) maybeCompact(sessionId, settings)
            val session = repo.find(sessionId) ?: return
            val apiMessages = buildApiMessages(session, settings)
            if (apiMessages.none { it.role == "user" || it.role == "tool" }) return

            val placeholder = ChatMessage(role = Role.ASSISTANT, status = MessageStatus.STREAMING)
            repo.appendMessage(sessionId, placeholder)
            val startedAt = System.currentTimeMillis()
            val content = StringBuilder()
            val reasoning = StringBuilder()
            var lastFlush = 0L

            fun flush() {
                repo.updateMessage(sessionId, placeholder.id) {
                    it.copy(content = content.toString(), reasoning = reasoning.toString())
                }
            }

            val result = try {
                streamWithRetry(settings, apiMessages, content, reasoning) {
                    val now = System.currentTimeMillis()
                    if (now - lastFlush >= 60) {
                        lastFlush = now
                        flush()
                    }
                }
            } catch (e: CancellationException) {
                repo.updateMessage(sessionId, placeholder.id) {
                    it.copy(
                        content = content.toString(),
                        reasoning = reasoning.toString(),
                        status = MessageStatus.CANCELLED,
                        durationMs = System.currentTimeMillis() - startedAt,
                        toolCalls = it.toolCalls.map { call ->
                            if (call.status == ToolStatus.RUNNING ||
                                call.status == ToolStatus.PENDING ||
                                call.status == ToolStatus.WAITING_USER
                            ) {
                                call.copy(status = ToolStatus.ERROR, result = "已停止（用户中断）")
                            } else {
                                call
                            }
                        },
                    )
                }
                throw e
            } catch (e: Exception) {
                repo.updateMessage(sessionId, placeholder.id) {
                    it.copy(
                        content = content.toString(),
                        reasoning = reasoning.toString(),
                        status = MessageStatus.ERROR,
                        error = e.message ?: "请求失败",
                        durationMs = System.currentTimeMillis() - startedAt,
                    )
                }
                return
            }

            val invocations = result.toolCalls.map { call ->
                ToolInvocation(id = call.id, name = call.name, arguments = call.arguments)
            }

            if (result.content.isEmpty() && invocations.isEmpty()) {
                repo.updateMessage(sessionId, placeholder.id) {
                    it.copy(
                        status = MessageStatus.ERROR,
                        error = "模型没有返回任何内容（finish_reason=${result.finishReason ?: "unknown"}）",
                        durationMs = System.currentTimeMillis() - startedAt,
                        reasoning = result.reasoning,
                    )
                }
                return
            }

            repo.updateMessage(sessionId, placeholder.id) {
                it.copy(
                    content = result.content,
                    reasoning = result.reasoning,
                    toolCalls = invocations,
                    status = MessageStatus.DONE,
                    durationMs = System.currentTimeMillis() - startedAt,
                    promptTokens = result.promptTokens,
                    completionTokens = result.completionTokens,
                    cachedTokens = result.cachedTokens,
                )
            }

            if (invocations.isEmpty()) {
                // 运行期间用户"插入"了新消息：继续一轮，让模型看到并调整方向
                val latest = repo.find(sessionId)
                val steered = latest?.messages?.any {
                    it.role == Role.USER && it.createdAt > placeholder.createdAt
                } == true
                if (steered && step < maxSteps) continue
                return
            }

            // 逐个执行工具，把状态实时推给 UI
            for (rawCall in invocations) {
                var call = rawCall
                if (call.name == "ssh_exec") {
                    val args = runCatching { org.json.JSONObject(call.arguments) }
                        .getOrDefault(org.json.JSONObject())
                    if (args.optString("server").isBlank()) {
                        val active = repo.find(sessionId)?.activeServerId.orEmpty()
                        if (active.isNotBlank()) {
                            args.put("server", active)
                            call = call.copy(arguments = args.toString())
                        }
                    }
                    val decision = RemoteCommandPolicy.evaluate(
                        args.optString("command"),
                        settings.permissionMode,
                    )
                    if (!decision.allowed) {
                        repo.updateMessage(sessionId, placeholder.id) { msg ->
                            msg.copy(toolCalls = msg.toolCalls.map {
                                if (it.id == call.id) {
                                    it.copy(status = ToolStatus.ERROR, result = decision.reason)
                                } else {
                                    it
                                }
                            })
                        }
                        continue
                    }
                    if (decision.needsApproval && !awaitToolApproval(sessionId, placeholder.id, call)) {
                        repo.updateMessage(sessionId, placeholder.id) { msg ->
                            msg.copy(toolCalls = msg.toolCalls.map {
                                if (it.id == call.id) {
                                    it.copy(status = ToolStatus.ERROR, result = "用户拒绝了这条远程命令。")
                                } else {
                                    it
                                }
                            })
                        }
                        continue
                    }
                }
                if (call.name == "update_tasks") {
                    repo.updateMessage(sessionId, placeholder.id) { msg ->
                        msg.copy(toolCalls = msg.toolCalls.map {
                            if (it.id == call.id) it.copy(status = ToolStatus.RUNNING) else it
                        })
                    }
                    val args = runCatching { org.json.JSONObject(call.arguments) }.getOrDefault(org.json.JSONObject())
                    val existing = repo.find(sessionId)?.tasks.orEmpty()
                    val arr = args.optJSONArray("tasks")
                    val tasks = buildList {
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val item = arr.optJSONObject(i) ?: continue
                                val text = item.optString("text").trim()
                                if (text.isEmpty()) continue
                                val status = runCatching {
                                    com.luoty.agent.data.TaskStatus.valueOf(
                                        item.optString("status", "PENDING").uppercase(),
                                    )
                                }.getOrDefault(com.luoty.agent.data.TaskStatus.PENDING)
                                add(
                                    com.luoty.agent.data.TaskItem(
                                        id = existing.getOrNull(i)?.id ?: UUID.randomUUID().toString(),
                                        text = text,
                                        status = status,
                                    ),
                                )
                            }
                        }
                    }
                    repo.setTasks(sessionId, tasks)
                    val done = tasks.count { it.status == com.luoty.agent.data.TaskStatus.DONE }
                    val doing = tasks.count { it.status == com.luoty.agent.data.TaskStatus.IN_PROGRESS }
                    val pending = tasks.size - done - doing
                    val result = "任务已更新：${done} 已完成 · ${doing} 进行中 · ${pending} 待处理"
                    repo.updateMessage(sessionId, placeholder.id) { msg ->
                        msg.copy(toolCalls = msg.toolCalls.map {
                            if (it.id == call.id) it.copy(result = result, status = ToolStatus.DONE) else it
                        })
                    }
                    continue
                }

                if (call.name == "ask_user") {
                    repo.updateMessage(sessionId, placeholder.id) { msg ->
                        msg.copy(toolCalls = msg.toolCalls.map {
                            if (it.id == call.id) it.copy(status = ToolStatus.RUNNING) else it
                        })
                    }
                    val args = runCatching { org.json.JSONObject(call.arguments) }.getOrDefault(org.json.JSONObject())
                    val question = args.optString("question", "这里需要你确认一下").ifBlank { "这里需要你确认一下" }
                    val options = buildList {
                        val arr = args.optJSONArray("options")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        }
                    }
                    val answer = askUser(sessionId, placeholder.id, call, question, options)
                    val result = if (answer.isNullOrBlank()) {
                        "用户跳过了这个问题，请自行决定，并说明理由。"
                    } else {
                        "用户选择：$answer"
                    }
                    repo.updateMessage(sessionId, placeholder.id) { msg ->
                        msg.copy(toolCalls = msg.toolCalls.map {
                            if (it.id == call.id) {
                                it.copy(result = result, status = ToolStatus.DONE)
                            } else {
                                it
                            }
                        })
                    }
                    continue
                }

                val denial = authorize(sessionId, placeholder.id, call, settings)
                if (denial != null) {
                    repo.updateMessage(sessionId, placeholder.id) { msg ->
                        msg.copy(toolCalls = msg.toolCalls.map {
                            if (it.id == call.id) {
                                it.copy(status = ToolStatus.ERROR, result = denial)
                            } else {
                                it
                            }
                        })
                    }
                    continue
                }

                repo.updateMessage(sessionId, placeholder.id) { msg ->
                    msg.copy(toolCalls = msg.toolCalls.map {
                        if (it.id == call.id) it.copy(status = ToolStatus.RUNNING) else it
                    })
                }
                val toolStart = System.currentTimeMillis()
                val outcome = tools.execute(call.name, call.arguments, workspaceFor(sessionId))
                val duration = System.currentTimeMillis() - toolStart
                repo.updateMessage(sessionId, placeholder.id) { msg ->
                    msg.copy(toolCalls = msg.toolCalls.map {
                        if (it.id == call.id) {
                            it.copy(
                                result = outcome.output,
                                status = if (outcome.isError) ToolStatus.ERROR else ToolStatus.DONE,
                                durationMs = duration,
                            )
                        } else {
                            it
                        }
                    })
                }
            }

            if (step >= maxSteps) {
                repo.appendMessage(
                    sessionId,
                    ChatMessage(
                        role = Role.ASSISTANT,
                        content = "已达到最大工具调用轮数（$maxSteps 轮），任务可能还没完成。可以继续发消息让我接着做，或在设置里调大「最大轮数」。",
                        status = MessageStatus.DONE,
                    ),
                )
                return
            }
        }
    }

    /** 返回 null 表示放行；返回字符串表示拒绝原因 */
    private suspend fun authorize(
        sessionId: String,
        messageId: String,
        call: ToolInvocation,
        settings: AppSettings,
    ): String? {
        val session = repo.find(sessionId)
        val write = call.name in WRITE_TOOLS
        if (session?.planMode == true) {
            val blocked = call.name in setOf(
                "write_file", "edit_file", "delete_path", "run_command",
                "ssh_exec", "start_web_server", "stop_web_server",
            )
            if (blocked) {
                return "计划模式：这个工具会改动文件或执行命令，已被拒绝。" +
                    "请只做调研（read_file / list_files / search_files / web_search），然后输出可执行的计划。" +
                    "用户确认计划后，切到「建造」再执行。"
            }
        }
        if (write && settings.permissionMode == PERM_READONLY) {
            return "只读模式：写操作被拒绝。可以在设置或输入栏切换权限。"
        }
        if (settings.permissionMode == PERM_SAFE) {
            when (call.name) {
                "delete_path" -> {
                    val path = runCatching {
                        org.json.JSONObject(call.arguments).optString("path", "")
                    }.getOrDefault("")
                    val count = tools.countAt(path)
                    if (count > 5 && !awaitToolApproval(sessionId, messageId, call)) {
                        return "用户拒绝了这次大量删除（$count 个文件）。"
                    }
                    if (!reviewDelete(sessionId, call, settings)) {
                        return "AI 审核拒绝了这次删除。请缩小范围，或先向用户说明要删什么。"
                    }
                }
                "web_search", "ssh_exec" -> {
                    if (!awaitToolApproval(sessionId, messageId, call)) {
                        return "用户拒绝了这次${if (call.name == "web_search") "联网搜索" else "服务器调用"}。"
                    }
                }
            }
        }
        return null
    }

    /** 删除动作交给一个独立的小模型审核；失败时放行（只作辅助，不保证安全） */
    private suspend fun reviewDelete(
        sessionId: String,
        call: ToolInvocation,
        settings: AppSettings,
    ): Boolean {
        val session = repo.find(sessionId)
        val root = runCatching { workspaceFor(sessionId).absolutePath }.getOrDefault("")
        val prompt = buildString {
            append("会话工作区：").append(root).append('\n')
            append("会话标题：").append(session?.title ?: "").append('\n')
            append("删除请求：").append(call.arguments.take(800)).append('\n')
            append("判断：是否只删工作区内文件、路径是否越界、是否像用户重要数据。")
            append("只回答 APPROVE 或 DENY: 原因。")
        }
        return runCatching {
            val verdict = client.complete(
                settings = settings,
                messages = listOf(
                    ApiMessage(
                        "system",
                        "你是文件操作审核员。只输出 APPROVE 或 DENY: 原因，不要输出其他内容。",
                    ),
                    ApiMessage("user", prompt),
                ),
                maxTokens = 120,
                temperature = 0.0,
            )
            !verdict.contains("DENY", ignoreCase = true)
        }.getOrDefault(true)
    }

    private suspend fun awaitToolApproval(
        sessionId: String,
        messageId: String,
        call: ToolInvocation,
    ): Boolean {
        repo.updateMessage(sessionId, messageId) { msg ->
            msg.copy(toolCalls = msg.toolCalls.map {
                if (it.id == call.id) it.copy(status = ToolStatus.WAITING_USER) else it
            })
        }
        val request = ApprovalRequest(UUID.randomUUID().toString(), sessionId, messageId, call)
        val deferred = CompletableDeferred<Boolean>()
        waiters[request.id] = deferred
        _approvals.update { it + request }
        return try {
            withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { deferred.await() } ?: false
        } finally {
            waiters.remove(request.id)
            _approvals.update { list -> list.filterNot { it.id == request.id } }
        }
    }

    private suspend fun streamWithRetry(
        settings: AppSettings,
        messages: List<ApiMessage>,
        content: StringBuilder,
        reasoning: StringBuilder,
        onProgress: () -> Unit,
    ): com.luoty.agent.net.StreamResult {
        var attempt = 0
        while (true) {
            attempt++
            try {
                return client.stream(
                    settings = settings,
                    messages = messages,
                    tools = tools.specs,
                    onText = {
                        content.append(it)
                        onProgress()
                    },
                    onReasoning = {
                        reasoning.append(it)
                        onProgress()
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                val retryable = e.code == null || e.code in 500..599 || e.code == 429
                if (attempt == 1 && retryable && content.isEmpty()) {
                    delay(1200)
                    continue
                }
                throw e
            } catch (e: Exception) {
                if (attempt == 1 && content.isEmpty()) {
                    delay(1200)
                    continue
                }
                throw e
            }
        }
    }

    private fun markLastStreamingError(sessionId: String, message: String) {
        val session = repo.find(sessionId) ?: return
        val last = session.messages.lastOrNull { it.role == Role.ASSISTANT && it.status == MessageStatus.STREAMING }
            ?: return
        repo.updateMessage(sessionId, last.id) {
            it.copy(status = MessageStatus.ERROR, error = message)
        }
    }

    // ---------------------------------------------------------------- 上下文构建

    private fun workspaceFor(sessionId: String): File {
        val session = repo.find(sessionId)
            ?: return File(tools.workspace, "sessions/$sessionId").also { it.mkdirs() }
        val dir = if (session.projectId.isNotBlank()) {
            File(tools.workspace, "projects/${session.projectId}")
        } else {
            File(tools.workspace, "sessions/${session.id}")
        }
        dir.mkdirs()
        if (session.projectId.isBlank()) {
            // 老版本的文件都在共享根目录，第一次进入会话文件夹时复制一份，避免“文件消失”
            val marker = File(dir, ".seeded")
            if (!marker.exists()) {
                runCatching {
                    tools.workspace.listFiles()?.forEach { source ->
                        if (source.name == "sessions" || source.name == "projects") return@forEach
                        val target = File(dir, source.name)
                        if (!target.exists()) {
                            if (source.isDirectory) {
                                source.copyRecursively(target, overwrite = false)
                            } else {
                                source.copyTo(target)
                            }
                        }
                    }
                    marker.createNewFile()
                }
            }
        }
        return dir
    }

    private fun buildApiMessages(session: Session, settings: AppSettings): List<ApiMessage> {
        val out = ArrayList<ApiMessage>()
        val basePrompt = SystemPrompt.build(
            settings = settings,
            workspacePath = workspaceFor(session.id).absolutePath,
            goal = if (session.goalEnabled) session.goal else "",
            planMode = session.planMode,
            summary = session.summary,
            summaryCount = session.summaryCount,
        )
        val servers = serverStore.load()
        val activeServer = servers.firstOrNull { it.id == session.activeServerId }
        out += ApiMessage("system", basePrompt)

        for (message in session.messages) {
            when (message.role) {
                Role.USER -> {
                    if (message.content.isNotBlank() || message.attachments.isNotEmpty()) {
                        out += userApiMessage(message, settings)
                    }
                }
                Role.ASSISTANT -> {
                    val completedCalls = message.toolCalls.filter { it.result != null }
                    if (message.content.isBlank() && completedCalls.isEmpty()) continue
                    out += ApiMessage(
                        role = "assistant",
                        content = message.content.ifBlank { null },
                        toolCalls = completedCalls.map { ApiToolCall(it.id, it.name, it.arguments) },
                    )
                    completedCalls.forEach { call ->
                        out += ApiMessage(
                            role = "tool",
                            content = call.result ?: "",
                            toolCallId = call.id,
                            name = call.name,
                        )
                    }
                }
            }
        }
        val runtimeMessage = ApiMessage(
            "system",
            SystemPrompt.runtimeContext(
                settings = settings,
                session = session,
                workspacePath = workspaceFor(session.id).absolutePath,
                serverLabel = activeServer?.let { "${it.label}（${it.target}）" }.orEmpty(),
            ),
        )
        val lastUser = out.indexOfLast { it.role == "user" }
        if (lastUser >= 0) out.add(lastUser, runtimeMessage) else out.add(runtimeMessage)
        return trimContext(out, settings)
    }

    /** 用户消息 → API 消息：图片走多模态 data URL，PDF 走抽取出的文本 */
    private fun userApiMessage(message: ChatMessage, settings: AppSettings): ApiMessage {
        val images = message.attachments.filter { it.kind == "image" }
        val docs = message.attachments.filter { it.kind == "pdf" || it.kind == "file" }
        val textBlock = buildString {
            append(message.content)
            docs.forEach { doc ->
                append("\n\n【").append(if (doc.kind == "pdf") "PDF" else "文件")
                    .append("：").append(doc.name).append("】\n")
                append(doc.extractedText?.take(40_000) ?: "（这个文件没有可提取的文本）")
            }
        }
        if (images.isEmpty()) return ApiMessage("user", textBlock)
        if (!settings.visionEnabled) {
            return ApiMessage(
                "user",
                textBlock + "\n\n（用户附带了 ${images.size} 张图片，但当前模型未开启多模态，图片未发送。）",
            )
        }
        val parts = mutableListOf(
            com.luoty.agent.net.ApiPart("text", text = textBlock.ifBlank { "请看这些图片。" }),
        )
        images.forEach { image ->
            attachmentStore.encodeImageDataUrl(image.path)?.let { url ->
                parts += com.luoty.agent.net.ApiPart("image_url", imageUrl = url)
            }
        }
        return ApiMessage("user", null, parts = parts)
    }

    /** 按上下文预算裁剪历史，保留最近的上下文；system 永远保留，且不把工具结果和它的调用拆开 */
    private fun trimContext(messages: List<ApiMessage>, settings: AppSettings): List<ApiMessage> {
        if (messages.size <= 2) return messages
        val budget = maxOf(20_000, settings.contextWindow * 3 - 4_000)
        var total = 0
        var start = messages.size
        for (i in messages.indices.reversed()) {
            val message = messages[i]
            val cost = (message.content?.length ?: 0) + 200
            if (total + cost > budget && start < messages.size) break
            total += cost
            start = i
        }
        if (start <= 1) return messages
        while (start < messages.size && messages[start].role == "tool") start++
        while (start < messages.size && messages[start].role == "assistant") start++
        if (start >= messages.size) return messages.take(1)
        return buildList {
            add(messages.first())
            addAll(messages.subList(start, messages.size))
        }
    }

    private companion object {
        const val APPROVAL_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
