package com.luoty.agent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luoty.agent.LuoAgentApp
import com.luoty.agent.data.AppSettings
import com.luoty.agent.data.ChatMessage
import com.luoty.agent.data.MessageStatus
import com.luoty.agent.data.Role
import com.luoty.agent.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen { CHAT, SETTINGS, WORKSPACE }

sealed interface ProbeState {
    data object Idle : ProbeState
    data object Loading : ProbeState
    data class Success(val message: String) : ProbeState
    data class Failure(val message: String) : ProbeState
}

data class WorkspaceEntry(
    val path: String,
    val size: Long,
    val modified: Long,
)

class AgentViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as LuoAgentApp).container
    private val settingsStore = container.settingsStore
    private val providerStore = container.providerStore
    private val serverStore = container.serverStore
    val repo = container.repo
    val engine = container.engine
    val tools = container.tools

    private val _servers = MutableStateFlow(serverStore.load())
    val servers: StateFlow<List<com.luoty.agent.data.SshServer>> = _servers.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _providers = MutableStateFlow(providerStore.load())
    val providers: StateFlow<List<com.luoty.agent.data.ModelProvider>> = _providers.asStateFlow()

    private val _activeProviderId = MutableStateFlow(providerStore.activeId())
    val activeProviderId: StateFlow<String?> = _activeProviderId.asStateFlow()

    val sessions = repo.sessions

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    val currentSession: StateFlow<Session?> = combine(sessions, currentSessionId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _screen = MutableStateFlow(Screen.CHAT)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _drafts = MutableStateFlow<Map<String, String>>(emptyMap())
    val drafts: StateFlow<Map<String, String>> = _drafts.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private val _probe = MutableStateFlow<ProbeState>(ProbeState.Idle)
    val probe: StateFlow<ProbeState> = _probe.asStateFlow()

    private val _workspaceFiles = MutableStateFlow<List<WorkspaceEntry>>(emptyList())
    val workspaceFiles: StateFlow<List<WorkspaceEntry>> = _workspaceFiles.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<Map<String, List<com.luoty.agent.data.Attachment>>>(emptyMap())
    val pendingAttachments: StateFlow<Map<String, List<com.luoty.agent.data.Attachment>>> =
        _pendingAttachments.asStateFlow()

    val approvals = engine.approvals
    val questions = engine.questions

    fun answerQuestion(questionId: String, answer: String?) {
        engine.answerQuestion(questionId, answer)
    }

    init {
        viewModelScope.launch {
            val wasRunning = mutableMapOf<String, Boolean>()
            sessions.collect { list ->
                val current = _currentSessionId.value
                if (list.isEmpty()) {
                    val created = repo.createSession()
                    _currentSessionId.value = created.id
                } else if (current == null || list.none { it.id == current }) {
                    _currentSessionId.value = list.first().id
                }
                // 一轮结束后自动发送队首消息
                for (session in list) {
                    val previous = wasRunning[session.id] ?: false
                    if (previous && !session.running && session.queue.isNotEmpty()) {
                        val next = repo.popQueue(session.id) ?: continue
                        repo.setRunning(session.id, true)
                        engine.send(session.id, next.text, _settings.value)
                    }
                    wasRunning[session.id] = session.running
                }
            }
        }
    }

    // ------------------------------------------------------------ 会话

    fun newSession() {
        val created = repo.createSession()
        _currentSessionId.value = created.id
        _screen.value = Screen.CHAT
    }

    fun selectSession(id: String) {
        _currentSessionId.value = id
        _screen.value = Screen.CHAT
    }

    fun deleteSession(id: String) {
        engine.stop(id)
        repo.deleteSession(id)
        _drafts.update { it - id }
    }

    fun renameSession(id: String, title: String) {
        repo.renameSession(id, title)
    }

    fun clearSession(id: String) {
        engine.stop(id)
        repo.truncateMessages(id, 0)
        repo.renameSession(id, "新会话")
    }

    fun goChat() {
        _screen.value = Screen.CHAT
    }

    // ------------------------------------------------------------ 权限 / 目标 / 计划 / 压缩

    fun approve(requestId: String, allow: Boolean) {
        engine.approve(requestId, allow)
    }

    fun setPermission(mode: String) {
        val updated = _settings.value.copy(permissionMode = mode)
        settingsStore.save(updated)
        _settings.value = updated
        toast("权限已切换为「${com.luoty.agent.data.permissionLabel(mode)}」")
    }

    fun setGoal(goal: String) {
        val session = currentSession.value ?: return
        repo.setGoal(session.id, goal)
        toast(if (goal.isBlank()) "已清除目标" else "目标已更新")
    }

    fun togglePlanMode() {
        val session = currentSession.value ?: return
        repo.setPlanMode(session.id, !session.planMode)
        toast(if (session.planMode) "已退出计划模式" else "已进入计划模式（只读）")
    }

    fun compactNow() {
        val session = currentSession.value ?: return
        if (session.running) {
            toast("正在运行，先停止再压缩")
            return
        }
        engine.compactNow(session.id, _settings.value)
        toast("正在压缩上下文…")
    }

    fun contextChars(session: Session?): Int {
        if (session == null) return 0
        var total = session.summary.length + 600
        for (message in session.messages) {
            total += message.content.length + message.reasoning.length
            for (call in message.toolCalls) {
                total += call.arguments.length + (call.result?.length ?: 0)
            }
        }
        return total
    }

    fun diagnostics(session: Session?): String = buildString {
        appendLine("# LuoTy Agent 诊断信息")
        appendLine()
        appendLine("- 版本：1.0.0")
        appendLine("- 设备：Android ${android.os.Build.VERSION.RELEASE}（API ${android.os.Build.VERSION.SDK_INT}）")
        appendLine("- 模型：" + _settings.value.model)
        appendLine("- 接口：" + _settings.value.baseUrl)
        appendLine("- 权限：" + com.luoty.agent.data.permissionLabel(_settings.value.permissionMode))
        appendLine("- 自动压缩：" + if (_settings.value.autoCompact) "开（阈值 ${_settings.value.compactThreshold} 字符）" else "关")
        appendLine("- 最大轮数：" + _settings.value.maxSteps)
        appendLine("- API Key：已配置（长度 ${_settings.value.apiKey.length}，不导出）")
        appendLine("- 工作区：" + tools.workspace.absolutePath)
        session?.let { s ->
            appendLine("- 会话：" + s.title + "（${s.messages.size} 条消息，摘要 ${s.summaryCount} 条）")
            appendLine("- 计划模式：" + if (s.planMode) "开" else "关")
            appendLine("- 本地上下文估算：" + contextChars(s) + " 字符")
            s.messages.lastOrNull { it.status == MessageStatus.ERROR }?.let {
                appendLine("- 最近错误：" + (it.error ?: ""))
            }
        }
    }

    fun openSettings() {
        _screen.value = Screen.SETTINGS
    }

    fun openWorkspace() {
        _screen.value = Screen.WORKSPACE
        refreshWorkspace()
    }

    // ------------------------------------------------------------ 对话

    fun draftFor(sessionId: String?): String = sessionId?.let { _drafts.value[it] } ?: ""

    fun setDraft(sessionId: String, text: String) {
        _drafts.update { it + (sessionId to text) }
    }

    fun send(text: String) {
        val session = currentSession.value ?: return
        val attachments = _pendingAttachments.value[session.id].orEmpty()
        if (text.isBlank() && attachments.isEmpty()) return
        if (!_settings.value.isReady) {
            toast("请先在设置里填写 API Key")
            _screen.value = Screen.SETTINGS
            return
        }
        if (attachments.any { it.kind == "image" } && !_settings.value.visionEnabled) {
            toast("当前模型未开启多模态，先去掉图片或到设置里开启")
            return
        }
        if (session.running || engine.isRunning(session.id)) {
            if (attachments.isNotEmpty()) {
                toast("繁忙时不支持附件，等这轮结束再发")
                return
            }
            when (_settings.value.busyBehavior) {
                com.luoty.agent.data.BUSY_STEER -> {
                    repo.appendMessage(
                        session.id,
                        ChatMessage(role = Role.USER, content = text.trim()),
                    )
                    setDraft(session.id, "")
                    toast("已插入会话，模型下一轮会看到并调整方向")
                    return
                }

                com.luoty.agent.data.BUSY_INTERRUPT -> {
                    engine.stop(session.id)
                    setDraft(session.id, "")
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(500)
                        engine.send(session.id, text, _settings.value)
                    }
                    toast("已打断当前生成，改按新消息继续")
                    return
                }

                else -> {
                    repo.enqueue(session.id, text)
                    setDraft(session.id, "")
                    toast("已加入排队（第 ${session.queue.size + 1} 位）")
                    return
                }
            }
        }
        setDraft(session.id, "")
        _pendingAttachments.update { it - session.id }
        engine.send(session.id, text, _settings.value, attachments)
    }

    fun attach(sessionId: String, uri: android.net.Uri, kind: String) {
        viewModelScope.launch {
            runCatching { container.attachments.import(uri, kind) }
                .onSuccess { attachment ->
                    _pendingAttachments.update { map ->
                        map + (sessionId to (map[sessionId].orEmpty() + attachment))
                    }
                    if (kind == "pdf" && attachment.extractedText.isNullOrBlank()) {
                        toast("这个 PDF 没抽到文字，可能是扫描件")
                    }
                }
                .onFailure { toast("附件导入失败：${it.message ?: "未知错误"}") }
        }
    }

    fun removeAttachment(sessionId: String, attachment: com.luoty.agent.data.Attachment) {
        _pendingAttachments.update { map ->
            map + (sessionId to map[sessionId].orEmpty().filterNot { it.id == attachment.id })
        }
        viewModelScope.launch(Dispatchers.IO) { container.attachments.delete(attachment) }
    }

    fun stop() {
        currentSession.value?.let { engine.stop(it.id) }
    }

    fun regenerate() {
        val session = currentSession.value ?: return
        if (session.running || engine.isRunning(session.id)) {
            toast("正在运行，先停止")
            return
        }
        engine.regenerate(session.id, _settings.value)
    }

    /** 兜底：进程被杀前落盘 */
    suspend fun flush() {
        repo.flush()
    }

    // ------------------------------------------------------------ 设置

    fun saveSettings(settings: AppSettings) {
        val cleaned = settings.copy(
            baseUrl = settings.baseUrl.trim().trimEnd('/'),
            apiKey = settings.apiKey.trim(),
            model = settings.model.trim(),
        )
        settingsStore.save(cleaned)
        _settings.value = cleaned
        // 当前生效的提供方跟随配置更新，避免列表和实际配置不一致
        val activeId = _activeProviderId.value
        if (activeId != null) {
            val list = _providers.value.map {
                if (it.id == activeId) {
                    it.copy(
                        baseUrl = cleaned.baseUrl,
                        apiKey = cleaned.apiKey,
                        model = cleaned.model,
                        extraHeaders = cleaned.extraHeaders,
                        models = it.models.map { cfg ->
                            if (cfg.id == cleaned.model) {
                                cfg.copy(vision = cleaned.visionEnabled, contextWindow = cleaned.contextWindow)
                            } else {
                                cfg
                            }
                        },
                    )
                } else {
                    it
                }
            }
            _providers.value = list
            providerStore.save(list)
        }
        toast("设置已保存")
    }

    // ------------------------------------------------------------ 多模型提供方

    fun saveProvider(provider: com.luoty.agent.data.ModelProvider, activate: Boolean) {
        val list = _providers.value
        val updated = if (list.any { it.id == provider.id }) {
            list.map { if (it.id == provider.id) provider else it }
        } else {
            list + provider
        }
        _providers.value = updated
        providerStore.save(updated)
        if (activate) {
            activateProvider(provider.id)
        } else {
            toast("提供方已保存")
        }
    }

    fun activateProvider(id: String) {
        val provider = _providers.value.firstOrNull { it.id == id } ?: return
        val config = provider.activeModel
        val updated = _settings.value.copy(
            baseUrl = provider.baseUrl.trim().trimEnd('/'),
            apiKey = provider.apiKey.trim(),
            model = config?.id ?: provider.model,
            extraHeaders = provider.extraHeaders,
            providerName = provider.name,
            visionEnabled = config?.vision ?: true,
            contextWindow = config?.contextWindow ?: 128_000,
        )
        settingsStore.save(updated)
        _settings.value = updated
        _activeProviderId.value = id
        providerStore.saveActiveId(id)
        toast("已切换到「${provider.name}」")
    }

    fun deleteProvider(id: String) {
        val list = _providers.value.filterNot { it.id == id }
        _providers.value = list
        providerStore.save(list)
        if (_activeProviderId.value == id) {
            _activeProviderId.value = null
            providerStore.saveActiveId(null)
        }
    }

    fun addOpenCodeGoPreset() {
        val existing = _providers.value.firstOrNull {
            it.baseUrl.contains("opencode.ai") && it.name.contains("OpenCode", ignoreCase = true)
        }
        if (existing != null) {
            activateProvider(existing.id)
            toast("已有 OpenCode Go 提供方，已直接切换")
            return
        }
        val preset = com.luoty.agent.data.openCodeGoPreset(apiKey = _settings.value.apiKey)
        saveProvider(preset, activate = true)
    }

    /** 拉取某个提供方的模型目录（设置页“获取可用模型”） */
    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        extraHeaders: String,
    ): Result<List<String>> {
        val probe = _settings.value.copy(
            baseUrl = baseUrl.trim().trimEnd('/'),
            apiKey = apiKey.trim(),
            extraHeaders = extraHeaders,
        )
        return runCatching { container.client.fetchModels(probe) }
    }

    fun setReasoningEffort(effort: String) {
        val updated = _settings.value.copy(reasoningEffort = effort)
        settingsStore.save(updated)
        _settings.value = updated
        toast("推理等级：${effortLabel(effort)}")
    }

    fun setTeamMode(enabled: Boolean) {
        val updated = _settings.value.copy(teamMode = enabled)
        settingsStore.save(updated)
        _settings.value = updated
        toast(if (enabled) "已开启团队协作模式" else "已关闭团队协作模式")
    }

    fun setVisionEnabled(enabled: Boolean) {
        val updated = _settings.value.copy(visionEnabled = enabled)
        settingsStore.save(updated)
        _settings.value = updated
        val activeId = _activeProviderId.value
        if (activeId != null) {
            val list = _providers.value.map { provider ->
                if (provider.id == activeId) {
                    provider.copy(
                        models = provider.models.map {
                            if (it.id == updated.model) it.copy(vision = enabled) else it
                        },
                    )
                } else {
                    provider
                }
            }
            _providers.value = list
            providerStore.save(list)
        }
    }

    fun setContextWindow(tokens: Int) {
        val updated = _settings.value.copy(contextWindow = tokens.coerceIn(2_000, 2_000_000))
        settingsStore.save(updated)
        _settings.value = updated
        val activeId = _activeProviderId.value
        if (activeId != null) {
            val list = _providers.value.map { provider ->
                if (provider.id == activeId) {
                    provider.copy(
                        models = provider.models.map {
                            if (it.id == updated.model) it.copy(contextWindow = updated.contextWindow) else it
                        },
                    )
                } else {
                    provider
                }
            }
            _providers.value = list
            providerStore.save(list)
        }
    }

    fun selectModel(model: String) {        val updated = _settings.value.copy(model = model)
        settingsStore.save(updated)
        _settings.value = updated
        val activeId = _activeProviderId.value
        if (activeId != null) {
            val list = _providers.value.map { if (it.id == activeId) it.copy(model = model) else it }
            _providers.value = list
            providerStore.save(list)
        }
        toast("已切换到模型「$model」")
    }

    fun setGoalEnabled(enabled: Boolean) {
        currentSession.value?.let { repo.setGoalEnabled(it.id, enabled) }
    }

    // ------------------------------------------------------------ 服务器

    fun saveServer(server: com.luoty.agent.data.SshServer) {
        val list = _servers.value
        val updated = if (list.any { it.id == server.id }) {
            list.map { if (it.id == server.id) server else it }
        } else {
            list + server
        }
        _servers.value = updated
        serverStore.save(updated)
        toast("服务器「${server.label}」已保存")
    }

    fun deleteServer(id: String) {
        val list = _servers.value.filterNot { it.id == id }
        _servers.value = list
        serverStore.save(list)
        repo.sessions.value.forEach { session ->
            if (session.activeServerId == id) repo.setActiveServer(session.id, "")
        }
    }

    fun setActiveServer(sessionId: String, serverId: String) {
        repo.setActiveServer(sessionId, serverId)
        val name = _servers.value.firstOrNull { it.id == serverId }?.label.orEmpty()
        toast(if (name.isBlank()) "已取消选中服务器" else "当前服务器：$name")
    }

    suspend fun testServer(server: com.luoty.agent.data.SshServer): Result<String> =
        runCatching { tools.testSsh(server) }

    // ------------------------------------------------------------ 排队 / 任务

    fun setBusyBehavior(mode: String) {
        val updated = _settings.value.copy(busyBehavior = mode)
        settingsStore.save(updated)
        _settings.value = updated
        toast("繁忙时：${com.luoty.agent.data.busyBehaviorLabel(mode)}")
    }

    fun updateQueued(sessionId: String, id: String, text: String) {
        repo.updateQueueItem(sessionId, id, text)
    }

    fun removeQueued(sessionId: String, id: String) {
        repo.removeQueueItem(sessionId, id)
    }

    fun clearQueue(sessionId: String) {
        repo.clearQueue(sessionId)
    }

    fun clearTasks(sessionId: String) {
        repo.setTasks(sessionId, emptyList())
    }

    /** 插队：移到队首；空闲时直接发送 */
    fun promoteQueued(sessionId: String, id: String) {
        repo.promoteQueueItem(sessionId, id)
        val session = repo.find(sessionId) ?: return
        if (!session.running && !engine.isRunning(sessionId)) {
            val next = repo.popQueue(sessionId) ?: return
            engine.send(sessionId, next.text, _settings.value)
        } else {
            toast("已插到队首，这一轮结束就发给它")
        }
    }

    fun resetSystemPrompt() {
        _settings.value = _settings.value.copy(systemPrompt = com.luoty.agent.data.DEFAULT_SYSTEM_PROMPT)
        settingsStore.save(_settings.value)
    }

    fun testConnection(settings: AppSettings) {
        if (_probe.value is ProbeState.Loading) return
        _probe.value = ProbeState.Loading
        viewModelScope.launch {
            val result = runCatching { container.client.testConnection(settings) }
            _probe.value = result.fold(
                onSuccess = { ProbeState.Success(it) },
                onFailure = { ProbeState.Failure(it.message ?: "连接失败") },
            )
        }
    }

    fun resetProbe() {
        _probe.value = ProbeState.Idle
    }

    // ------------------------------------------------------------ 工作区

    fun refreshWorkspace() {
        viewModelScope.launch {
            _workspaceFiles.value = withContext(Dispatchers.IO) {
                runCatching {
                    tools.workspace.walkTopDown()
                        .filter { it.isFile }
                        .take(1000)
                        .map { WorkspaceEntry(tools.rel(it), it.length(), it.lastModified()) }
                        .sortedBy { it.path }
                        .toList()
                }.getOrDefault(emptyList())
            }
        }
    }

    suspend fun readWorkspaceFile(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = tools.resolve(path)
            if (!file.isFile) return@runCatching null
            if (file.length() > 600_000) return@runCatching "（文件太大，已省略显示，共 ${file.length()} 字节）"
            val bytes = file.readBytes()
            if (bytes.any { it == 0.toByte() }) "（二进制文件，无法预览）" else String(bytes, Charsets.UTF_8)
        }.getOrNull()
    }

    fun deleteWorkspaceFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { tools.resolve(path).deleteRecursively() }
            refreshWorkspace()
            toast("已删除 $path")
        }
    }

    fun clearWorkspace() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                tools.workspace.listFiles()?.forEach { it.deleteRecursively() }
            }
            refreshWorkspace()
            toast("工作区已清空")
        }
    }

    // ------------------------------------------------------------ 导出

    fun exportMarkdown(session: Session): String = buildString {
        append("# ").append(session.title).append("\n\n")
        append("> 由 LuoTy Agent 导出 · ").append(formatTime(System.currentTimeMillis())).append("\n\n")
        for (message in session.messages) {
            when (message.role) {
                Role.USER -> append("## 用户\n\n").append(message.content).append("\n\n")
                Role.ASSISTANT -> {
                    append("## 助手\n\n")
                    if (message.reasoning.isNotBlank()) {
                        append("<details><summary>思考过程</summary>\n\n")
                        append(message.reasoning).append("\n\n</details>\n\n")
                    }
                    if (message.content.isNotBlank()) append(message.content).append("\n\n")
                    for (call in message.toolCalls) {
                        append("**工具 `").append(call.name).append("`**\n\n```json\n")
                        append(prettyJson(call.arguments)).append("\n```\n\n")
                        call.result?.let {
                            append("```\n").append(it.take(4000)).append("\n```\n\n")
                        }
                    }
                    if (message.status == MessageStatus.CANCELLED) append("_（已停止）_\n\n")
                    message.error?.let { append("> 错误：").append(it).append("\n\n") }
                }
            }
        }
    }

    fun toast(message: String) {
        _events.tryEmit(message)
    }

    private companion object {
        fun formatTime(time: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(time))
    }
}

fun effortLabel(effort: String): String = when (effort) {
    "low" -> "低"
    "medium" -> "中"
    "high" -> "高"
    "max" -> "最高"
    else -> "Default"
}

fun prettyJson(raw: String): String {    val text = raw.trim()
    if (text.isEmpty()) return "{}"
    return runCatching {
        val json = org.json.JSONObject(text)
        json.toString(2)
    }.getOrElse { text }
}
