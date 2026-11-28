package com.luoty.agent.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ModelTraining
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import com.luoty.agent.data.Attachment
import com.luoty.agent.data.SshServer
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luoty.agent.agent.ApprovalRequest
import com.luoty.agent.agent.UserQuestion
import com.luoty.agent.data.AppSettings
import com.luoty.agent.data.QueuedMessage
import com.luoty.agent.data.TaskItem
import com.luoty.agent.data.TaskStatus
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.ui.text.style.TextDecoration
import com.luoty.agent.data.ChatMessage
import com.luoty.agent.data.MessageStatus
import com.luoty.agent.data.PERM_SAFE
import com.luoty.agent.data.PERM_FULL
import com.luoty.agent.data.PERM_READONLY
import com.luoty.agent.data.Role
import com.luoty.agent.data.Session
import com.luoty.agent.data.ToolInvocation
import com.luoty.agent.data.ToolStatus
import com.luoty.agent.data.permissionLabel
import com.luoty.agent.ui.theme.AppColors
import com.luoty.agent.ui.theme.Mono
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SlashCommand(
    val label: String,
    val en: String,
    val hint: String,
    val icon: ImageVector,
) {
    FILES("文件", "file", "打开工作区，查看 Agent 写的文件", Icons.Rounded.FolderOpen),
    GOAL("目标", "goal", "设置或查看长期任务目标", Icons.Rounded.Flag),
    PLAN("计划", "plan", "进入/退出计划模式（只读）", Icons.Rounded.Checklist),
    FEEDBACK("反馈", "feedback", "复制诊断信息，方便反馈问题", Icons.Rounded.BugReport),
    COMPACT("压缩", "compact", "把早期对话压缩成摘要，腾出上下文", Icons.Rounded.Compress),
    PERMISSION("权限", "permission", "切换权限预设：完全 / 只读 / 每次确认", Icons.Rounded.Shield),
    SERVER("服务器", "server", "选择要直接操控的远程 Linux 服务器", Icons.Rounded.Dns),
}

@Composable
fun ChatScreen(
    vm: AgentViewModel,
    session: Session?,
    settings: AppSettings,
    onMenu: () -> Unit,
) {
    val messages = session?.messages.orEmpty()
    var visibleTurns by rememberSaveable(session?.id) { mutableStateOf(3) }
    val windowSize = visibleTurns * 2 + 2
    val visibleMessages = if (messages.size <= windowSize) messages else messages.takeLast(windowSize)
    val hiddenCount = messages.size - visibleMessages.size
    val listItemCount = visibleMessages.size + (if (hiddenCount > 0) 1 else 0) + 1
    val running = session?.running == true
    val sessionId = session?.id
    val drafts by vm.drafts.collectAsStateWithLifecycle()
    val approvals by vm.approvals.collectAsStateWithLifecycle()
    val questions by vm.questions.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val activeProviderId by vm.activeProviderId.collectAsStateWithLifecycle()
    val servers by vm.servers.collectAsStateWithLifecycle()
    val activeServer = servers.firstOrNull { it.id == session?.activeServerId }
    var showServerSheet by remember { mutableStateOf(false) }
    val modelOptions = providers.firstOrNull { it.id == activeProviderId }?.models?.map { it.id }.orEmpty()
    val draft = sessionId?.let { drafts[it] } ?: ""
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var followBottom by remember { mutableStateOf(true) }

    val mineApprovals = approvals.filter { it.sessionId == sessionId }
    val mineQuestions = questions.filter { it.sessionId == sessionId }
    val pendingMap by vm.pendingAttachments.collectAsStateWithLifecycle()
    val pending = sessionId?.let { pendingMap[it] }.orEmpty()
    var showModelSheet by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && sessionId != null) vm.attach(sessionId, uri, "image")
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && sessionId != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.attach(sessionId, uri, "pdf")
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && sessionId != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.attach(sessionId, uri, "file")
        }
    }

    LaunchedEffect(listState) {
        var scrolling = false
        snapshotFlow { listState.isScrollInProgress }.collect { active ->
            if (active) {
                scrolling = true
            } else if (scrolling) {
                scrolling = false
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                followBottom = last == null || last.index >= info.totalItemsCount - 2
            }
        }
    }

    LaunchedEffect(session?.id) {
        if (messages.isNotEmpty()) {
            followBottom = true
            listState.scrollToItem(listItemCount - 1)
        }
    }

    val lastMessage = messages.lastOrNull()
    LaunchedEffect(messages.size, lastMessage, mineApprovals.size) {
        if (messages.isNotEmpty() && followBottom) {
            listState.scrollToItem(listItemCount - 1)
        }
    }

    val lastAssistantId = messages.lastOrNull { it.role == Role.ASSISTANT }?.id

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AppTopBar(
            title = session?.title ?: "LuoTy Agent",
            subtitle = when {
                session?.compacting == true -> "正在压缩上下文…"
                running -> "Agent 运行中…"
                session?.planMode == true -> "计划模式 · ${settings.model}"
                else -> settings.model
            },
            onMenu = onMenu,
            actions = {
                IconButton(onClick = { vm.newSession() }) {
                    Icon(Icons.Rounded.Add, contentDescription = "新建会话")
                }
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("工作区") },
                        leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) },
                        onClick = { showMenu = false; vm.openWorkspace() },
                    )
                    DropdownMenuItem(
                        text = { Text("设置") },
                        leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                        onClick = { showMenu = false; vm.openSettings() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (session?.planMode == true) "退出计划模式" else "进入计划模式") },
                        leadingIcon = { Icon(Icons.Rounded.Checklist, null) },
                        onClick = { showMenu = false; vm.togglePlanMode() },
                    )
                    DropdownMenuItem(
                        text = { Text("压缩上下文") },
                        leadingIcon = { Icon(Icons.Rounded.Compress, null) },
                        onClick = { showMenu = false; vm.compactNow() },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DropdownMenuItem(
                        text = { Text("导出为 Markdown") },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                        enabled = session != null && messages.isNotEmpty(),
                        onClick = {
                            showMenu = false
                            session?.let {
                                clipboard.setText(AnnotatedString(vm.exportMarkdown(it)))
                                Toast.makeText(context, "会话已复制为 Markdown", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("清空当前会话") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        enabled = session != null && messages.isNotEmpty(),
                        onClick = {
                            showMenu = false
                            sessionId?.let { vm.clearSession(it) }
                        },
                    )
                }
            },
        )

        if (!settings.isReady) {
            ApiKeyBanner(onClick = vm::openSettings)
        }

        Box(Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                EmptyState(
                    hasKey = settings.isReady,
                    onPick = { prompt -> sessionId?.let { vm.setDraft(it, prompt) } },
                    onOpenSettings = vm::openSettings,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (hiddenCount > 0) {
                        item(key = "load-more") {
                            TextButton(
                                onClick = { visibleTurns += 3 },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "加载更早的 $hiddenCount 条对话",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    items(visibleMessages, key = { it.id }) { message ->
                        MessageItem(
                            message = message,
                            showReasoning = settings.showReasoning,
                            isFinalAnswer = message.id == lastAssistantId,
                            canRetry = message === lastMessage &&
                                message.role == Role.ASSISTANT &&
                                message.status != MessageStatus.STREAMING,
                            onRetry = vm::regenerate,
                            onCopy = {
                                clipboard.setText(AnnotatedString(message.content))
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }
                if (!followBottom) {
                    ScrollToBottom(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                    ) {
                        scope.launch { listState.animateScrollToItem(listItemCount - 1) }
                        followBottom = true
                    }
                }
            }
        }

        Composer(
            value = draft,
            running = running,
            enabled = session != null,
            planMode = session?.planMode == true,
            onTogglePlanMode = { vm.togglePlanMode() },
            compacting = session?.compacting == true,
            summaryCount = session?.summaryCount ?: 0,
            permission = settings.permissionMode,
            model = settings.model,
            contextChars = vm.contextChars(session),
            contextLimit = settings.contextWindow,
            activeServer = activeServer?.label.orEmpty(),
            onOpenServerSheet = { showServerSheet = true },
            goal = session?.goal.orEmpty(),
            goalEnabled = session?.goalEnabled ?: true,
            tasks = session?.tasks.orEmpty(),
            queue = session?.queue.orEmpty(),
            onTaskClear = { sessionId?.let { vm.clearTasks(it) } },
            onQueueEdit = { id, text -> sessionId?.let { vm.updateQueued(it, id, text) } },
            onQueuePromote = { id -> sessionId?.let { vm.promoteQueued(it, id) } },
            onQueueRemove = { id -> sessionId?.let { vm.removeQueued(it, id) } },
            onQueueClear = { sessionId?.let { vm.clearQueue(it) } },
            approvals = mineApprovals,
            question = mineQuestions.firstOrNull(),
            pendingAttachments = pending,
            visionEnabled = settings.visionEnabled,
            onValueChange = { text -> sessionId?.let { vm.setDraft(it, text) } },
            onSend = { if (draft.isNotBlank()) vm.send(draft) },
            onStop = vm::stop,
            onCommand = { command ->
                when (command) {
                    SlashCommand.FILES -> vm.openWorkspace()
                    SlashCommand.GOAL -> showGoalDialog = true
                    SlashCommand.PLAN -> vm.togglePlanMode()
                    SlashCommand.FEEDBACK -> {
                        clipboard.setText(AnnotatedString(vm.diagnostics(session)))
                        Toast.makeText(context, "诊断信息已复制，可直接发给开发者", Toast.LENGTH_SHORT).show()
                    }
                    SlashCommand.COMPACT -> vm.compactNow()
                    SlashCommand.PERMISSION -> Unit // 权限在 chip 上直接切换
                    SlashCommand.SERVER -> showServerSheet = true
                }
                sessionId?.let { vm.setDraft(it, "") }
            },
            onPermission = vm::setPermission,
            onApprove = { requestId, allow -> vm.approve(requestId, allow) },
            onAnswerQuestion = { id, answer -> vm.answerQuestion(id, answer) },
            onEditGoal = { showGoalDialog = true },
            onToggleGoal = {
                vm.setGoalEnabled(!(session?.goalEnabled ?: true))
            },
            onAttach = { kind ->
                when (kind) {
                    "image" -> {
                        if (!settings.visionEnabled) {
                            Toast.makeText(context, "当前模型未开启多模态，先去设置打开", Toast.LENGTH_SHORT).show()
                        } else {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    }
                    "pdf" -> pdfPicker.launch(arrayOf("application/pdf"))
                    else -> filePicker.launch(arrayOf("*/*"))
                }
            },
            onRemoveAttachment = { attachment -> sessionId?.let { vm.removeAttachment(it, attachment) } },
            onOpenModelSheet = { showModelSheet = true },
            onOpenSettings = vm::openSettings,
        )
    }

    if (showModelSheet) {
        ModelSheet(
            model = settings.model,
            modelOptions = modelOptions,
            effort = settings.reasoningEffort,
            teamMode = settings.teamMode,
            onSelectModel = vm::selectModel,
            onEffort = vm::setReasoningEffort,
            onTeamMode = vm::setTeamMode,
            onManage = {
                showModelSheet = false
                vm.openSettings()
            },
            onDismiss = { showModelSheet = false },
        )
    }

    if (showServerSheet) {
        ServerSheet(
            servers = servers,
            activeId = session?.activeServerId.orEmpty(),
            onSelect = { id ->
                sessionId?.let { vm.setActiveServer(it, id) }
                showServerSheet = false
            },
            onManage = {
                showServerSheet = false
                vm.openSettings()
            },
            onDismiss = { showServerSheet = false },
        )
    }

    if (showGoalDialog) {
        GoalDialog(
            initial = session?.goal.orEmpty(),
            enabled = session?.goalEnabled ?: true,
            onDismiss = { showGoalDialog = false },
            onConfirm = { text, enable ->
                vm.setGoal(text)
                vm.setGoalEnabled(enable)
                showGoalDialog = false
            },
            onClear = {
                vm.setGoal("")
                showGoalDialog = false
            },
        )
    }
}

// ---------------------------------------------------------------- 顶栏

@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onMenu: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            onBack != null -> IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", modifier = Modifier.size(24.dp))
            }
            onMenu != null -> IconButton(onClick = onMenu) {
                Icon(Icons.Rounded.Menu, contentDescription = "会话列表", modifier = Modifier.size(26.dp))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

// ---------------------------------------------------------------- 空状态 / 输入栏

@Composable
private fun EmptyState(hasKey: Boolean, onPick: (String) -> Unit, onOpenSettings: () -> Unit) {
    val suggestions = listOf(
        "看看工作区里有什么文件，帮我梳理一下项目结构",
        "用 Kotlin 写一个待办清单 App 的核心数据层代码",
        "新建一个 index.html，做一个有动效的个人主页",
        "写一个 Python 脚本：统计工作区里每个文件的行数",
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LogoBadge(size = 64)
        Spacer(Modifier.height(16.dp))
        Text("LuoTy Agent", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "手机上的 AI 编码 Agent\n能读写文件、执行命令、多轮自检地完成任务",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!hasKey) {
            Spacer(Modifier.height(18.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("还没有配置 API Key", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "填上 DeepSeek（或任意 OpenAI 兼容）接口即可开始",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                    TextButton(onClick = onOpenSettings) { Text("去设置") }
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            suggestions.forEach { text ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(text) },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Rounded.Send,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyBanner(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "未配置 API Key，点这里去设置",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun Composer(
    value: String,
    running: Boolean,
    enabled: Boolean,
    planMode: Boolean,
    onTogglePlanMode: () -> Unit,
    compacting: Boolean,
    summaryCount: Int,
    permission: String,
    model: String,
    contextChars: Int,
    contextLimit: Int,
    activeServer: String,
    onOpenServerSheet: () -> Unit,
    goal: String,
    goalEnabled: Boolean,
    tasks: List<TaskItem>,
    queue: List<QueuedMessage>,
    onTaskClear: () -> Unit,
    onQueueEdit: (String, String) -> Unit,
    onQueuePromote: (String) -> Unit,
    onQueueRemove: (String) -> Unit,
    onQueueClear: () -> Unit,
    approvals: List<ApprovalRequest>,
    question: UserQuestion?,
    pendingAttachments: List<Attachment>,
    visionEnabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onCommand: (SlashCommand) -> Unit,
    onPermission: (String) -> Unit,
    onApprove: (String, Boolean) -> Unit,
    onAnswerQuestion: (String, String?) -> Unit,
    onEditGoal: () -> Unit,
    onToggleGoal: () -> Unit,
    onAttach: (String) -> Unit,
    onRemoveAttachment: (Attachment) -> Unit,
    onOpenModelSheet: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showSlash by remember { mutableStateOf(false) }
    var showPermissionMenu by remember { mutableStateOf(false) }
    var showFullPermDialog by remember { mutableStateOf(false) }
    var riskAccepted by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var editingQueue by remember { mutableStateOf<QueuedMessage?>(null) }
    if (editingQueue != null) {
        val target = editingQueue!!
        QueueEditDialog(
            initial = target.text,
            onDismiss = { editingQueue = null },
            onSave = {
                onQueueEdit(target.id, it)
                editingQueue = null
            },
        )
    }
    val slashTyping = value.startsWith("/") && !value.contains(" ") && value.length <= 12
    val query = if (slashTyping) value.removePrefix("/").trim() else ""
    val commands = SlashCommand.entries.filter {
        query.isEmpty() || it.label.contains(query, ignoreCase = true) || it.en.contains(query, ignoreCase = true)
    }
    val menuOpen = showSlash || slashTyping

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (planMode) {
                HintBar(
                    icon = Icons.Rounded.Checklist,
                    text = "计划模式：只会读文件、出计划；写操作会被拒绝",
                    color = Color(0xFFD7BA7D),
                )
            }
            if (tasks.isNotEmpty()) {
                TaskPanel(tasks = tasks, onClear = onTaskClear)
            }
            if (queue.isNotEmpty()) {
                QueuePanel(
                    queue = queue,
                    onEdit = { id, _ -> editingQueue = queue.firstOrNull { it.id == id } },
                    onPromote = onQueuePromote,
                    onRemove = onQueueRemove,
                    onClear = onQueueClear,
                )
            }
            if (summaryCount > 0) {
                HintBar(
                    icon = Icons.Rounded.Compress,
                    text = "已把 $summaryCount 条早期消息压缩成摘要",
                    color = AppColors.Success,
                )
            }
            if (goal.isNotBlank()) {
                GoalBar(
                    goal = goal,
                    enabled = goalEnabled,
                    onToggle = onToggleGoal,
                    onEdit = onEditGoal,
                )
            }

            question?.let { pending ->
                QuestionCard(
                    question = pending,
                    onAnswer = { answer -> onAnswerQuestion(pending.id, answer) },
                    onSkip = { onAnswerQuestion(pending.id, null) },
                )
            }

            approvals.firstOrNull()?.let { ApprovalCard(it, onApprove) }

            if (pendingAttachments.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pendingAttachments.forEach { attachment ->
                        PendingAttachmentChip(attachment) { onRemoveAttachment(attachment) }
                    }
                }
            }

            if (menuOpen) {
                SlashPanel(
                    commands = commands,
                    onPick = { command ->
                        showSlash = false
                        onCommand(command)
                    },
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(26.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(
                    Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    IconButton(
                        onClick = { showSlash = !showSlash },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "指令",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showAttachMenu = true },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Rounded.AttachFile,
                                contentDescription = "添加附件",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("照片") },
                            leadingIcon = { Icon(Icons.Rounded.Image, null) },
                            onClick = {
                                showAttachMenu = false
                                onAttach("image")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("PDF 文档") },
                            leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, null) },
                            onClick = {
                                showAttachMenu = false
                                onAttach("pdf")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("其他文件（Word / TXT / ZIP…）") },
                            leadingIcon = { Icon(Icons.Rounded.Description, null) },
                            onClick = {
                                showAttachMenu = false
                                onAttach("file")
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    placeholder = {
                        Text(
                            "描述你想构建的内容，/ 调出指令…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 6,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.width(6.dp))
                if (running && value.isNotBlank()) {
                    FilledIconButton(
                        onClick = onStop,
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = "停止", modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                }
                FilledIconButton(
                    onClick = if (running && value.isBlank()) onStop else onSend,
                    enabled = running || (enabled && value.isNotBlank()),
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (running && value.isBlank()) {
                        Icon(Icons.Rounded.Stop, contentDescription = "停止", modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Rounded.Send, contentDescription = "发送", modifier = Modifier.size(18.dp))
                    }
                }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Chip(
                        icon = Icons.Rounded.Shield,
                        text = permissionLabel(permission),
                        tint = if (permission == PERM_FULL) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFD7BA7D),
                        onClick = { showPermissionMenu = true },
                    )
                    DropdownMenu(expanded = showPermissionMenu, onDismissRequest = { showPermissionMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("安全模式 · 删除/搜索/服务器才确认") },
                            onClick = { onPermission(PERM_SAFE); showPermissionMenu = false },
                        )
                        DropdownMenuItem(
                            text = { Text("只读 · 拒绝写文件和命令") },
                            onClick = { onPermission(PERM_READONLY); showPermissionMenu = false },
                        )
                        DropdownMenuItem(
                            text = { Text("完全权限 · 直接执行工具") },
                            onClick = {
                                showPermissionMenu = false
                                riskAccepted = false
                                showFullPermDialog = true
                            },
                        )
                    }
                    if (showFullPermDialog) {
                        AlertDialog(
                            onDismissRequest = { showFullPermDialog = false },
                            title = { Text("确认启用完全权限？") },
                            text = {
                                Column {
                                    Text(
                                        "启用完全权限后，智能体会减少确认步骤，并且可以直接执行更多操作，" +
                                            "包括敏感操作、文件修改或外部命令。仅建议在你信任当前任务时使用。",
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = riskAccepted,
                                            onCheckedChange = { riskAccepted = it },
                                        )
                                        Text("我已了解风险，并愿意继续", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    enabled = riskAccepted,
                                    onClick = {
                                        onPermission(PERM_FULL)
                                        riskAccepted = false
                                        showFullPermDialog = false
                                    },
                                ) { Text("启用完全权限") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showFullPermDialog = false }) { Text("取消") }
                            },
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Chip(
                    icon = if (planMode) Icons.Rounded.Map else Icons.Rounded.Build,
                    text = if (planMode) "计划" else "建造",
                    tint = if (planMode) Color(0xFFD7BA7D) else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onTogglePlanMode,
                )
                Spacer(Modifier.width(8.dp))
                if (activeServer.isNotBlank()) {
                    Chip(
                        icon = Icons.Rounded.Dns,
                        text = activeServer,
                        tint = Color(0xFF5FD3C1),
                        onClick = onOpenServerSheet,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Chip(
                    icon = Icons.Rounded.ModelTraining,
                    text = model,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onOpenModelSheet,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = when {
                        compacting -> "压缩中…"
                        contextChars > 0 -> "${contextChars / 3000}k / ${contextLimit / 1000}k tokens"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                )
            }
        }
    }
}

@Composable
private fun SlashPanel(commands: List<SlashCommand>, onPick: (SlashCommand) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(vertical = 6.dp),
    ) {
        Text(
            "指令",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 4.dp),
        )
        if (commands.isEmpty()) {
            Text(
                "没有匹配的指令",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
        commands.forEach { command ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(command) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    command.icon,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(command.label, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(6.dp))
                Text(
                    command.en,
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    command.hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
            }
        }
    }
}

@Composable
private fun Chip(icon: ImageVector, text: String, tint: Color, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HintBar(icon: ImageVector, text: String, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.9f))
    }
}

@Composable
private fun TaskPanel(tasks: List<TaskItem>, onClear: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val done = tasks.count { it.status == TaskStatus.DONE }
    val doing = tasks.count { it.status == TaskStatus.IN_PROGRESS }
    val pending = tasks.size - done - doing
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Checklist,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("任务", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    "$done 已完成 · $doing 进行中 · $pending 待处理",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (expanded) {
                tasks.forEach { task ->
                    Row(
                        Modifier.padding(start = 12.dp, end = 10.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (task.status) {
                            TaskStatus.DONE -> Icon(
                                Icons.Rounded.Check,
                                null,
                                tint = AppColors.Success,
                                modifier = Modifier.size(12.dp),
                            )
                            TaskStatus.IN_PROGRESS -> CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.4.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            TaskStatus.PENDING -> Icon(
                                Icons.Rounded.RadioButtonUnchecked,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(12.dp),
                            )
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(
                            task.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (task.status == TaskStatus.DONE) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textDecoration = if (task.status == TaskStatus.DONE) {
                                TextDecoration.LineThrough
                            } else {
                                null
                            },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClear, modifier = Modifier.height(26.dp)) {
                        Text("清空任务", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueuePanel(
    queue: List<QueuedMessage>,
    onEdit: (String, String) -> Unit,
    onPromote: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
    ) {
        Column {
            Row(
                Modifier.padding(start = 10.dp, end = 6.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Schedule,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "排队中 ${queue.size} 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClear, modifier = Modifier.height(26.dp)) {
                    Text("清空", style = MaterialTheme.typography.labelSmall)
                }
            }
            queue.forEachIndexed { index, item ->
                Row(
                    Modifier.padding(start = 10.dp, end = 2.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(16.dp),
                    )
                    Text(
                        item.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onPromote(item.id) }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Rounded.ArrowUpward,
                            contentDescription = "插队",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    IconButton(onClick = { onEdit(item.id, item.text) }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "修改",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    IconButton(onClick = { onRemove(item.id) }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueEditDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改排队消息") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun GoalBar(goal: String, enabled: Boolean, onToggle: () -> Unit, onEdit: () -> Unit) {
    val tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            )
            .clickable(onClick = onEdit)
            .padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Flag,
            null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = if (enabled) "目标：$goal" else "目标（已暂停）：$goal",
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
            Icon(
                if (enabled) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (enabled) "暂停目标" else "启用目标",
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Rounded.Edit,
                contentDescription = "编辑目标",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun QuestionCard(question: UserQuestion, onAnswer: (String) -> Unit, onSkip: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.HelpOutline,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "需要你选一下",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "Agent 在等你",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                question.question,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (question.options.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    question.options.forEach { option ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAnswer(option) },
                        ) {
                            Text(
                                option,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSkip) {
                    Text("跳过，让你自己决定", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(request: ApprovalRequest, onApprove: (String, Boolean) -> Unit) {
    Surface(
        color = Color(0xFF2A2314),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .border(1.dp, Color(0xFF7A6428), RoundedCornerShape(12.dp)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Shield,
                    null,
                    tint = Color(0xFFD7BA7D),
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "需要确认：${request.call.name}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFF0DBA8),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "等待授权",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFD7BA7D).copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = toolSummary(request.call).ifBlank { request.call.arguments.take(200) },
                fontFamily = Mono,
                fontSize = 11.5.sp,
                color = Color(0xFFE8D7AE),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onApprove(request.id, false) }) {
                    Text("拒绝", color = Color(0xFFE8D7AE))
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = { onApprove(request.id, true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD7BA7D),
                        contentColor = Color(0xFF241C0A),
                    ),
                    modifier = Modifier.height(34.dp),
                ) {
                    Text("允许一次", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun GoalDialog(
    initial: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit,
    onClear: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var isEnabled by remember { mutableStateOf(enabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("会话目标") },
        text = {
            Column {
                Text(
                    "设置后，Agent 每次请求都会带着这个长期目标；也可以随时暂停。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 3,
                    maxLines = 6,
                    placeholder = { Text("例如：完成一个可用的记账 App，先做数据层") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用目标", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text, isEnabled) }) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

// ---------------------------------------------------------------- 消息

@Composable
private fun MessageItem(
    message: ChatMessage,
    showReasoning: Boolean,
    isFinalAnswer: Boolean,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
) {
    when (message.role) {
        Role.USER -> UserBubble(message)
        Role.ASSISTANT -> AssistantMessage(
            message = message,
            showReasoning = showReasoning,
            isFinalAnswer = isFinalAnswer,
            canRetry = canRetry,
            onRetry = onRetry,
            onCopy = onCopy,
        )
    }
}

@Composable
private fun UserBubble(message: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = AppColors.UserBubble,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp),
            modifier = Modifier.widthIn(max = 330.dp),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                val images = message.attachments.filter { it.kind == "image" }
                if (images.isNotEmpty()) {
                    Row(
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        images.take(4).forEach { image ->
                            val bitmap = remember(image.path) {
                                BitmapFactory.decodeFile(
                                    image.path,
                                    BitmapFactory.Options().apply { inSampleSize = 6 },
                                )?.asImageBitmap()
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = image.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(9.dp)),
                                )
                            }
                        }
                    }
                }
                message.attachments.filter { it.kind == "pdf" || it.kind == "file" }.forEach { doc ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF24314F))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (doc.kind == "pdf") Icons.Rounded.PictureAsPdf else Icons.Rounded.Description,
                            null,
                            tint = AppColors.UserBubbleText.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            doc.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.UserBubbleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (message.content.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.UserBubbleText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingAttachmentChip(attachment: Attachment, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            Modifier.padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (attachment.kind == "image") {
                val bitmap = remember(attachment.path) {
                    BitmapFactory.decodeFile(
                        attachment.path,
                        BitmapFactory.Options().apply { inSampleSize = 8 },
                    )?.asImageBitmap()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = attachment.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                } else {
                    Icon(
                        Icons.Rounded.Image,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Icon(
                    if (attachment.kind == "pdf") Icons.Rounded.PictureAsPdf else Icons.Rounded.Description,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                attachment.name.take(20),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(26.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "移除附件",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerSheet(
    servers: List<SshServer>,
    activeId: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 30.dp),
        ) {
            Text("服务器", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "选中后，Agent 的 ssh_exec 默认在这台机器上执行；危险命令会先请求你确认。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            if (servers.isEmpty()) {
                Text(
                    "还没有添加服务器，去设置 → 服务器里加一台。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            servers.forEach { server ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (server.id == activeId) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                        )
                        .clickable { onSelect(server.id) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (server.id == activeId) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        null,
                        tint = if (server.id == activeId) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(server.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            server.target,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (server.trustedFingerprint.isBlank()) {
                        Text(
                            "未信任指纹",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD7BA7D),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                Text("管理服务器")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(
    model: String,
    modelOptions: List<String>,
    effort: String,
    teamMode: Boolean,
    onSelectModel: (String) -> Unit,
    onEffort: (String) -> Unit,
    onTeamMode: (Boolean) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showModels by remember { mutableStateOf(false) }
    var showEffort by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 30.dp),
        ) {
            Text("模型", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showModels = !showModels }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("模型", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            model.ifBlank { "未选择" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            if (showModels) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (showModels) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        if (modelOptions.isEmpty()) {
                            Text(
                                "还没有模型目录，去设置里「获取可用模型」",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                        modelOptions.take(60).forEach { id ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectModel(id)
                                        showModels = false
                                        onDismiss()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (id == model) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                if (id == model) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showEffort = !showEffort }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("推理等级", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            effortLabel(effort),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            if (showEffort) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (showEffort) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        listOf("default", "low", "medium", "high", "max").forEach { value ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onEffort(value)
                                        showEffort = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    effortLabel(value),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (value == effort) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("启用智能体团队协作", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "按架构 / 实现 / 审查三个视角推进任务",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = teamMode, onCheckedChange = onTeamMode)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                Text("管理模型与上下文")
            }
        }
    }
}

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    showReasoning: Boolean,
    isFinalAnswer: Boolean,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val intermediate = !isFinalAnswer && message.toolCalls.isNotEmpty()
    val collapsed = intermediate && !expanded

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoBadge(size = 22)
            Spacer(Modifier.width(8.dp))
            Text(
                "LuoTy",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (intermediate) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "中间步骤",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.weight(1f))
            if (message.status == MessageStatus.DONE && message.content.isNotBlank()) {
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "复制回答",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        if (showReasoning && message.reasoning.isNotBlank() && !intermediate) {
            ReasoningPanel(message)
            Spacer(Modifier.height(8.dp))
        }

        if (message.content.isNotBlank()) {
            if (collapsed) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expanded = true }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message.content.replace("\n", " ").take(90),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "展开",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                SelectionContainer {
                    MarkdownView(
                        markdown = message.content,
                        streaming = message.status == MessageStatus.STREAMING,
                    )
                }
                if (intermediate) {
                    TextButton(
                        onClick = { expanded = false },
                        modifier = Modifier.height(28.dp),
                    ) {
                        Text("收起", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (message.toolCalls.isNotEmpty()) {
            Spacer(Modifier.height(if (message.content.isBlank()) 0.dp else 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                message.toolCalls.forEach { call -> ToolCallCard(call) }
            }
        }

        when (message.status) {
            MessageStatus.STREAMING -> {
                if (message.content.isBlank() && message.reasoning.isBlank()) {
                    Spacer(Modifier.height(10.dp))
                    ThinkingRow()
                } else if (message.content.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "生成中…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            MessageStatus.ERROR -> {
                Spacer(Modifier.height(10.dp))
                ErrorPanel(message.error ?: "请求失败", canRetry, onRetry)
            }

            MessageStatus.CANCELLED -> {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "已停止生成",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (canRetry) {
                        TextButton(onClick = onRetry, modifier = Modifier.height(30.dp)) {
                            Icon(Icons.Rounded.Replay, null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("重新生成", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            MessageStatus.DONE -> {
                if (message.content.isNotBlank() || message.toolCalls.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = buildString {
                            append(formatClock(message.createdAt))
                            if (message.durationMs > 0) append(" · ").append(formatDuration(message.durationMs))
                            if (message.completionTokens > 0) {
                                append(" · ").append(message.promptTokens).append("→").append(message.completionTokens)
                                append(" tokens")
                            }
                            if (message.promptTokens > 0 && message.cachedTokens > 0) {
                                append(" · 缓存 ").append(message.cachedTokens * 100 / message.promptTokens).append("%")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReasoningPanel(message: ChatMessage) {
    var expanded by remember(message.id) { mutableStateOf(message.status == MessageStatus.STREAMING) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(11.dp),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Lightbulb,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (message.status == MessageStatus.STREAMING) "深度思考中…" else "深度思考",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (expanded) {
                Text(
                    text = message.reasoning,
                    fontFamily = Mono,
                    fontSize = 11.5.sp,
                    lineHeight = 18.sp,
                    color = AppColors.Reasoning,
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ThinkingRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(13.dp),
            strokeWidth = 1.6.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            "思考中…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorPanel(error: String, canRetry: Boolean, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "出错了",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.weight(1f))
                if (canRetry) {
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Replay, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重试")
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

// ---------------------------------------------------------------- 工具调用卡片

@Composable
private fun ToolCallCard(call: ToolInvocation) {
    var expanded by remember(call.id) { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = when (call.status) {
            ToolStatus.ERROR -> AppColors.Danger.copy(alpha = 0.45f)
            ToolStatus.RUNNING, ToolStatus.WAITING_USER -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else -> AppColors.ToolCardBorder
        },
        label = "toolBorder",
    )
    Surface(
        color = AppColors.ToolCard,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    toolIcon(call.name),
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    call.name,
                    fontFamily = Mono,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val summary = toolSummary(call)
                if (summary.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        summary,
                        fontFamily = Mono,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                ToolStatusBadge(call)
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                    Text(
                        "参数",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.CodeBg)
                            .horizontalScroll(rememberScrollState())
                            .padding(8.dp),
                    ) {
                        Text(
                            text = prettyJson(call.arguments).take(4000),
                            fontFamily = Mono,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            softWrap = false,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    call.result?.let { result ->
                        Spacer(Modifier.height(9.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (call.isError) "错误输出" else "输出",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (call.isError) AppColors.Danger else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            if (call.durationMs > 0) {
                                Text(
                                    formatDuration(call.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                text = result,
                                fontFamily = Mono,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = if (call.isError) AppColors.Danger else Color(0xFFC8D2E0),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(rememberScrollState())
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AppColors.CodeBg)
                                    .padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolStatusBadge(call: ToolInvocation) {
    when (call.status) {
        ToolStatus.RUNNING, ToolStatus.PENDING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.6.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
        }
        ToolStatus.WAITING_USER -> {
            Text(
                "待确认",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFD7BA7D),
            )
            Spacer(Modifier.width(6.dp))
        }
        ToolStatus.DONE -> {
            Icon(
                Icons.Rounded.Check,
                null,
                tint = AppColors.Success,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (call.durationMs > 0) formatDuration(call.durationMs) else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
        }
        ToolStatus.ERROR -> {
            Icon(
                Icons.Rounded.ErrorOutline,
                null,
                tint = AppColors.Danger,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
    }
}

private fun toolIcon(name: String): ImageVector = when (name) {
    "read_file" -> Icons.Rounded.Description
    "write_file" -> Icons.Rounded.Edit
    "edit_file" -> Icons.Rounded.Edit
    "list_files" -> Icons.Rounded.FolderOpen
    "search_files" -> Icons.Rounded.Search
    "delete_path" -> Icons.Rounded.Delete
    "run_command" -> Icons.Rounded.Terminal
    else -> Icons.Rounded.Build
}

private fun toolSummary(call: ToolInvocation): String {
    val json = runCatching { org.json.JSONObject(call.arguments.ifBlank { "{}" }) }.getOrNull() ?: return ""
    return when (call.name) {
        "list_files" -> json.optString("path").let { if (it.isBlank() || it == ".") "工作区根目录" else it }
        "read_file", "write_file", "edit_file", "delete_path" -> json.optString("path")
        "search_files" -> "「" + json.optString("query") + "」" +
            json.optString("path").let { if (it.isBlank() || it == ".") "" else "  in $it" }
        "run_command" -> "$ " + json.optString("command")
        else -> call.arguments.take(80)
    }
}

// ---------------------------------------------------------------- 小部件

@Composable
fun LogoBadge(size: Int = 26) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.3f).dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF3D5AFE), Color(0xFF7C4DFF)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            ">_",
            fontFamily = Mono,
            fontSize = (size * 0.42f).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun ScrollToBottom(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
        shadowElevation = 6.dp,
        modifier = modifier
            .size(36.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = "回到底部",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun formatClock(time: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(time))

private fun formatDuration(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> String.format(Locale.US, "%.1fs", ms / 1000.0)
    else -> "${ms / 60_000}m${(ms % 60_000) / 1000}s"
}
