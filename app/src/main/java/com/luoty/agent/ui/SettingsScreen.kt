package com.luoty.agent.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import com.luoty.agent.data.PROVIDER_PRESETS
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luoty.agent.data.AppSettings
import com.luoty.agent.data.BUSY_INTERRUPT
import com.luoty.agent.data.BUSY_QUEUE
import com.luoty.agent.data.BUSY_STEER
import com.luoty.agent.data.DEFAULT_SYSTEM_PROMPT
import com.luoty.agent.data.MODEL_PRESETS
import com.luoty.agent.data.ModelConfig
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ModelTraining
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.luoty.agent.data.SshServer
import com.luoty.agent.data.ModelProvider
import com.luoty.agent.data.PERM_SAFE
import com.luoty.agent.data.PERM_FULL
import com.luoty.agent.data.PERM_READONLY
import com.luoty.agent.ui.theme.AppColors
import com.luoty.agent.ui.theme.Mono
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.UUID

private enum class SettingsSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    MODELS("模型与接口", "提供方、模型目录、当前接口", Icons.Rounded.ModelTraining),
    SERVERS("远程服务器", "SSH 主机与安全审核", Icons.Rounded.Dns),
    SEARCH("联网搜索", "搜索 API 与 DuckDuckGo 回退", Icons.Rounded.Search),
    GENERATION("生成与行为", "温度、轮数、繁忙策略、标题", Icons.Rounded.Tune),
    CONTEXT("权限与上下文", "权限预设、压缩、多模态", Icons.Rounded.Compress),
    PROMPT("系统提示词", "只读核心上下文 + 附加指令", Icons.Rounded.Description),
    WORKSPACE("工作区", "文件目录、清空", Icons.Rounded.FolderOpen),
    ABOUT("关于", "版本与隐私说明", Icons.Rounded.Info),
}

@Composable
private fun SettingsMenu(
    providerCount: Int,
    serverCount: Int,
    onSelect: (SettingsSection) -> Unit,
) {
    SettingsCard("设置") {
        SettingsSection.entries.forEach { entry ->
            val hint = when (entry) {
                SettingsSection.MODELS -> " · $providerCount 个"
                SettingsSection.SERVERS -> " · $serverCount 台"
                else -> ""
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(entry) }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    entry.icon,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        entry.subtitle + hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Rounded.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
fun SettingsScreen(vm: AgentViewModel, settings: AppSettings, onBack: () -> Unit) {
    var section by remember { mutableStateOf<SettingsSection?>(null) }
    var draft by remember { mutableStateOf(settings) }
    var showKey by remember { mutableStateOf(false) }
    var showClearWorkspace by remember { mutableStateOf(false) }
    var showProviderDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ModelProvider?>(null) }
    var deletingProvider by remember { mutableStateOf<ModelProvider?>(null) }
    var showServerDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<SshServer?>(null) }
    var deletingServer by remember { mutableStateOf<SshServer?>(null) }
    val probe by vm.probe.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val activeProviderId by vm.activeProviderId.collectAsStateWithLifecycle()
    val servers by vm.servers.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AppTopBar(
            title = section?.title ?: "设置",
            subtitle = section?.subtitle ?: "模型、搜索、服务器、权限与上下文",
            onBack = {
                if (section != null) section = null else onBack()
            },
            actions = {
                if (section != null) {
                    TextButton(onClick = { vm.saveSettings(draft) }) { Text("保存") }
                }
            },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (section == null) {
                SettingsMenu(
                    providerCount = providers.size,
                    serverCount = servers.size,
                    onSelect = { section = it },
                )
            }
            if (section == SettingsSection.MODELS) {
                SettingsCard("模型提供方") {
                    Text(
                        if (providers.isEmpty()) {
                            "还没有保存的提供方。可以手动添加，或先用内置的 OpenCode Go 预设。"
                        } else {
                            "点击切换当前使用的提供方；每个提供方独立保存地址、密钥和模型。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    providers.forEach { provider ->
                        ProviderRow(
                            provider = provider,
                            active = provider.id == activeProviderId,
                            onActivate = {
                                vm.activateProvider(provider.id)
                                draft = vm.settings.value
                                vm.resetProbe()
                            },
                            onEdit = {
                                editingProvider = provider
                                showProviderDialog = true
                            },
                            onDelete = { deletingProvider = provider },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            editingProvider = null
                            showProviderDialog = true
                        }) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("添加")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            vm.addOpenCodeGoPreset()
                            draft = vm.settings.value
                        }) {
                            Text("OpenCode Go 预设")
                        }
                    }
                }
                SettingsCard("接口") {
                    LabeledField(
                        label = "Base URL",
                        value = draft.baseUrl,
                        placeholder = "https://api.deepseek.com",
                        onValueChange = { draft = draft.copy(baseUrl = it) },
                    )
                    OutlinedTextField(
                        value = draft.apiKey,
                        onValueChange = { draft = draft.copy(apiKey = it) },
                        label = { Text("API Key") },
                        placeholder = { Text("sk-…") },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "显示/隐藏",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ModelSelector(draft.model) { draft = draft.copy(model = it) }
                    if (draft.providerName.isNotBlank()) {
                        Text(
                            "当前提供方：${draft.providerName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = draft.extraHeaders,
                        onValueChange = { draft = draft.copy(extraHeaders = it) },
                        label = { Text("额外请求头（可选）") },
                        placeholder = { Text("x-opencode-session: \${session}") },
                        minLines = 1,
                        maxLines = 3,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "每行一个 Name: value；\${session} 会替换成随机会话 ID（OpenCode Go 需要）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { vm.testConnection(draft) },
                            enabled = probe !is ProbeState.Loading,
                        ) {
                            if (probe is ProbeState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 1.6.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("测试连接")
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "支持任何 OpenAI 兼容服务",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ProbeLine(probe)
                }
            }
            if (section == SettingsSection.SERVERS) {
                SettingsCard("服务器") {
                    Text(
                        "添加 Linux 服务器后，Agent 可以在你授权下通过 SSH 执行命令；" +
                            "主机指纹首次连接需要你确认，所有命令都会写入本机审计日志。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    servers.forEach { server ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Dns,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(server.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${server.target} · " + if (server.trustedFingerprint.isBlank()) {
                                        "未信任主机指纹"
                                    } else {
                                        "指纹已信任"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = {
                                editingServer = server
                                showServerDialog = true
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = "编辑",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            IconButton(onClick = { deletingServer = server }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                    OutlinedButton(onClick = {
                        editingServer = null
                        showServerDialog = true
                    }) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (servers.isEmpty()) "添加服务器" else "再加一台")
                    }
                }
            }
            if (section == SettingsSection.SEARCH) {
                SettingsCard("联网搜索") {
                    Text(
                        "Agent 需要时效信息时会调用 web_search。优先用搜索 API；没配 Key 或调用失败会自动退回 DuckDuckGo。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PermissionPill("关闭", draft.searchProvider == "none") {
                            draft = draft.copy(searchProvider = "none")
                        }
                        PermissionPill("Tavily", draft.searchProvider == "tavily") {
                            draft = draft.copy(searchProvider = "tavily")
                        }
                        PermissionPill("Brave", draft.searchProvider == "brave") {
                            draft = draft.copy(searchProvider = "brave")
                        }
                        PermissionPill("Serper", draft.searchProvider == "serper") {
                            draft = draft.copy(searchProvider = "serper")
                        }
                    }
                    OutlinedTextField(
                        value = draft.searchApiKey,
                        onValueChange = { draft = draft.copy(searchApiKey = it) },
                        label = { Text("搜索 API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Tavily: tavily.com · Brave: api.search.brave.com · Serper: serper.dev；留空则只用 DuckDuckGo。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            if (section == SettingsSection.GENERATION) {
                SettingsCard("生成参数") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("温度", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            String.format("%.1f", draft.temperature),
                            fontFamily = Mono,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = draft.temperature,
                        onValueChange = { draft = draft.copy(temperature = (it * 10).roundToInt() / 10f) },
                        valueRange = 0f..1.5f,
                    )
                    Text(
                        "越低越稳定、越适合改代码；越高越发散",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("最大输出（tokens，最多 7 位）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(128_000, 256_000, 512_000, 1_000_000).forEach { preset ->
                            PermissionPill(
                                label = compactTokens(preset),
                                selected = draft.maxTokens == preset,
                                onClick = { draft = draft.copy(maxTokens = preset) },
                            )
                        }
                    }
                    NumberField(
                        label = "最大输出 tokens",
                        value = draft.maxTokens,
                        range = 256..9_999_999,
                        onValueChange = { draft = draft.copy(maxTokens = it) },
                    )
                    NumberField(
                        label = "最大工具轮数",
                        value = draft.maxSteps,
                        range = 1..60,
                        onValueChange = { draft = draft.copy(maxSteps = it) },
                    )
                }
                SettingsCard("行为") {
                    Text("繁忙时的发送行为", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            BUSY_QUEUE to "排队",
                            BUSY_STEER to "插入",
                            BUSY_INTERRUPT to "打断",
                        ).forEach { (mode, label) ->
                            PermissionPill(
                                label = label,
                                selected = draft.busyBehavior == mode,
                                onClick = { draft = draft.copy(busyBehavior = mode) },
                            )
                        }
                    }
                    Text(
                        "智能体运行时发送消息：排队会按顺序等这轮结束；插入会立刻追加进会话、下一轮模型能看到并调整方向；打断会停掉当前生成再发。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SwitchRow(
                        title = "自动生成会话标题",
                        subtitle = "用第一条消息命名新会话",
                        checked = draft.autoTitle,
                        onCheckedChange = { draft = draft.copy(autoTitle = it) },
                    )
                    SwitchRow(
                        title = "显示思考过程",
                        subtitle = "deepseek-reasoner 等模型的 reasoning 内容",
                        checked = draft.showReasoning,
                        onCheckedChange = { draft = draft.copy(showReasoning = it) },
                    )
                }
            }
            if (section == SettingsSection.CONTEXT) {
                SettingsCard("权限与上下文") {
                    Text("权限预设", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            PERM_FULL to "完全权限",
                            PERM_SAFE to "安全模式",
                            PERM_READONLY to "只读",
                        ).forEach { (mode, label) ->
                            PermissionPill(
                                label = label,
                                selected = draft.permissionMode == mode,
                                onClick = { draft = draft.copy(permissionMode = mode) },
                            )
                        }
                    }
                    Text(
                        when (draft.permissionMode) {
                            PERM_READONLY -> "写文件、删文件、执行命令都会被拒绝，只允许读取和搜索。"
                            PERM_SAFE -> "每次写操作都会在聊天里弹出确认卡片，点「允许一次」才执行。"
                            else -> "工具直接执行，不再询问。信任当前模型时最快。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow(
                        title = "自动压缩上下文",
                        subtitle = "接近阈值时把早期消息压缩成摘要，省 token 也不丢目标",
                        checked = draft.autoCompact,
                        onCheckedChange = { draft = draft.copy(autoCompact = it) },
                    )
                    SwitchRow(
                        title = "多模态（可发图片）",
                        subtitle = "关闭后图片附件不会发送给模型，PDF 文本不受影响",
                        checked = draft.visionEnabled,
                        onCheckedChange = { draft = draft.copy(visionEnabled = it) },
                    )
                    Text("输入上下文（tokens，最多 7 位）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(128_000, 256_000, 512_000, 1_000_000).forEach { preset ->
                            PermissionPill(
                                label = compactTokens(preset),
                                selected = draft.contextWindow == preset,
                                onClick = { draft = draft.copy(contextWindow = preset) },
                            )
                        }
                    }
                    NumberField(
                        label = "输入上下文 tokens",
                        value = draft.contextWindow,
                        range = 16_000..9_999_999,
                        onValueChange = { draft = draft.copy(contextWindow = it) },
                    )
                    NumberField(
                        label = "压缩阈值（字符）",
                        value = draft.compactThreshold,
                        range = 20_000..400_000,
                        onValueChange = { draft = draft.copy(compactThreshold = it) },
                    )
                    NumberField(
                        label = "压缩后保留最近消息",
                        value = draft.compactKeepRecent,
                        range = 2..20,
                        onValueChange = { draft = draft.copy(compactKeepRecent = it) },
                    )
                }
            }
            if (section == SettingsSection.PROMPT) {
                SettingsCard("系统提示词") {
                    OutlinedTextField(
                        value = draft.systemPrompt,
                        onValueChange = { draft = draft.copy(systemPrompt = it) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        maxLines = 14,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Row {
                        TextButton(onClick = { draft = draft.copy(systemPrompt = DEFAULT_SYSTEM_PROMPT) }) {
                            Text("恢复默认")
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "决定 Agent 的做事方式",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
            }
            if (section == SettingsSection.WORKSPACE) {
                SettingsCard("工作区") {
                    Text(
                        "Agent 只能读写这个目录里的文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        color = AppColors.CodeBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = vm.tools.workspace.absolutePath,
                            fontFamily = Mono,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { vm.openWorkspace() }) { Text("浏览文件") }
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(onClick = { showClearWorkspace = true }) {
                            Text("清空工作区", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (section == SettingsSection.ABOUT) {

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "LuoTy Agent v1.0.0\nAPI Key 只保存在本机私有存储（SharedPreferences），不会上传到除你配置的接口之外的任何地方。\n" +
                        "多会话并发请求、工具调用循环、流式输出均为原生 Kotlin 实现。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showServerDialog) {
        ServerDialog(
            vm = vm,
            initial = editingServer,
            onDismiss = { showServerDialog = false },
            onSave = { server ->
                vm.saveServer(server)
                showServerDialog = false
            },
        )
    }

    deletingServer?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingServer = null },
            title = { Text("删除服务器") },
            text = { Text("确定删除「${target.label}」吗？保存的密码/私钥会一起删除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteServer(target.id)
                    deletingServer = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deletingServer = null }) { Text("取消") } },
        )
    }

    if (deletingProvider != null) {        val target = deletingProvider!!
        AlertDialog(
            onDismissRequest = { deletingProvider = null },
            title = { Text("删除提供方") },
            text = { Text("确定删除「${target.name}」吗？密钥也会一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteProvider(target.id)
                    deletingProvider = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProvider = null }) { Text("取消") }
            },
        )
    }

    if (showProviderDialog) {
        ProviderDialog(
            vm = vm,
            initial = editingProvider,
            onDismiss = { showProviderDialog = false },
            onSave = { provider ->
                val wasActive = provider.id == activeProviderId
                vm.saveProvider(provider, activate = wasActive)
                if (wasActive) draft = vm.settings.value
                showProviderDialog = false
            },
        )
    }

    if (showClearWorkspace) {
        AlertDialog(
            onDismissRequest = { showClearWorkspace = false },
            title = { Text("清空工作区") },
            text = { Text("会删除工作区里的所有文件和子目录，不可恢复。确定继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearWorkspace = false
                    vm.clearWorkspace()
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearWorkspace = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun PermissionPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ServerDialog(
    vm: AgentViewModel,
    initial: SshServer?,
    onDismiss: () -> Unit,
    onSave: (SshServer) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(initial?.username ?: "root") }
    var authType by remember { mutableStateOf(initial?.authType ?: "password") }
    var secret by remember { mutableStateOf(initial?.secret ?: "") }
    var passphrase by remember { mutableStateOf(initial?.passphrase ?: "") }
    var trusted by remember { mutableStateOf(initial?.trustedFingerprint ?: "") }
    var testing by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    var offeredFingerprint by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun draft(): SshServer = SshServer(
        id = initial?.id ?: UUID.randomUUID().toString(),
        name = name.trim(),
        host = host.trim(),
        port = port.toIntOrNull()?.coerceIn(1, 65535) ?: 22,
        username = username.trim(),
        authType = authType,
        secret = secret,
        passphrase = passphrase,
        trustedFingerprint = trusted,
        createdAt = initial?.createdAt ?: System.currentTimeMillis(),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加服务器" else "编辑服务器") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("例如：生产机 / 测试机") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("主机地址") },
                    placeholder = { Text("1.2.3.4 或 example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text("端口") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(124.dp),
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PermissionPill("密码登录", authType == "password") { authType = "password" }
                    PermissionPill("私钥登录", authType == "key") { authType = "key" }
                }
                if (authType == "password") {
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = { Text("私钥（PEM / OpenSSH）") },
                        minLines = 4,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("私钥密码（可选）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            if (host.isBlank() || username.isBlank() || secret.isBlank()) {
                                testMessage = "主机、用户名、密码/私钥都要填"
                                testOk = false
                                return@OutlinedButton
                            }
                            testing = true
                            testMessage = null
                            offeredFingerprint = null
                            scope.launch {
                                val result = vm.testServer(draft())
                                testing = false
                                result.fold(
                                    onSuccess = {
                                        testOk = true
                                        testMessage = it
                                    },
                                    onFailure = { error ->
                                        testOk = false
                                        testMessage = error.message ?: "连接失败"
                                        val fp = Regex("指纹：(.+)").find(error.message.orEmpty())
                                            ?.groupValues?.getOrNull(1)?.trim()
                                        if (!fp.isNullOrBlank() && !fp.contains("未知")) offeredFingerprint = fp
                                    },
                                )
                            }
                        },
                        enabled = !testing,
                    ) {
                        if (testing) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 1.5.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("测试连接")
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        when {
                            trusted.isBlank() -> "未信任指纹"
                            else -> "指纹已信任"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                testMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (testOk) AppColors.Success else MaterialTheme.colorScheme.error,
                    )
                }
                offeredFingerprint?.let { fingerprint ->
                    TextButton(onClick = {
                        trusted = fingerprint
                        offeredFingerprint = null
                        testMessage = "指纹已记录，再点一次「测试连接」验证"
                        testOk = false
                    }) { Text("信任此指纹并保存（$fingerprint）", maxLines = 2) }
                }
                Text(
                    "密码与私钥会用 Android Keystore 加密后保存；" +
                        "危险命令（删除、改权限、重启服务等）执行前会要求确认。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft()) },
                enabled = name.isNotBlank() && host.isNotBlank() && username.isNotBlank() && secret.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ProviderRow(
    provider: ModelProvider,
    active: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onActivate),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 2.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (active) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (active) "使用中" else "切换到该提供方",
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(provider.name, style = MaterialTheme.typography.bodyMedium)
                    if (active) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "使用中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    "${provider.model} · ${provider.host}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderDialog(
    vm: AgentViewModel,
    initial: ModelProvider?,
    onDismiss: () -> Unit,
    onSave: (ModelProvider) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var headers by remember { mutableStateOf(initial?.extraHeaders ?: "") }
    var model by remember { mutableStateOf(initial?.model ?: "") }
    var configs by remember { mutableStateOf(initial?.models.orEmpty()) }
    var fetched by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var fetching by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ModelConfig?>(null) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加模型提供方" else "编辑提供方") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "常用预设",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PROVIDER_PRESETS.forEach { preset ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.clickable {
                                if (name.isBlank()) name = preset.name
                                baseUrl = preset.baseUrl
                                if (headers.isBlank() && preset.extraHeaders.isNotBlank()) headers = preset.extraHeaders
                                if (configs.isEmpty()) {
                                    configs = listOf(
                                        ModelConfig(
                                            id = preset.model,
                                            name = preset.model,
                                            vision = preset.vision,
                                            contextWindow = preset.contextWindow,
                                        ),
                                    )
                                    model = preset.model
                                }
                            },
                        ) {
                            Text(
                                preset.name,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("显示名称") },
                    placeholder = { Text("例如：OpenCode Go") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API 地址") },
                    placeholder = { Text("https://api.deepseek.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "协议：OpenAI Chat Completions（兼容 DeepSeek / OpenCode / 各类中转）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = headers,
                    onValueChange = { headers = it },
                    label = { Text("额外请求头（每行 Name: value）") },
                    placeholder = { Text("x-opencode-session: \${session}") },
                    minLines = 1,
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            if (baseUrl.isBlank()) {
                                fetchError = "先填 API 地址"
                                return@OutlinedButton
                            }
                            fetching = true
                            fetchError = null
                            scope.launch {
                                val result = vm.fetchModels(baseUrl, apiKey, headers)
                                fetching = false
                                result.fold(
                                    onSuccess = { list ->
                                        fetched = list
                                        if (list.isEmpty()) fetchError = "接口没有返回任何模型"
                                    },
                                    onFailure = { fetchError = it.message ?: "获取失败" },
                                )
                            }
                        },
                        enabled = !fetching,
                    ) {
                        if (fetching) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 1.5.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("获取可用模型")
                    }
                    Spacer(Modifier.weight(1f))
                    if (fetched.isNotEmpty()) {
                        Text(
                            "已获取 ${fetched.size} · 已选 ${selected.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                fetchError?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (fetched.isNotEmpty()) {
                    Text(
                        "点选要保留的模型，再把它们加入目录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        fetched.take(150).forEach { id ->
                            val isSelected = id in selected
                            Surface(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(9.dp),
                                modifier = Modifier.clickable {
                                    selected = if (isSelected) selected - id else selected + id
                                },
                            ) {
                                Text(
                                    id,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            val added = selected
                                .map { id -> configs.firstOrNull { it.id == id } ?: ModelConfig(id = id, name = id) }
                            configs = (configs + added).distinctBy { it.id }
                            if (model.isBlank()) model = configs.firstOrNull()?.id.orEmpty()
                            selected = emptySet()
                        },
                        enabled = selected.isNotEmpty(),
                    ) { Text("加入模型目录（${selected.size}）") }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "模型目录",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { adding = true }) { Text("+ 添加模型") }
                }
                if (configs.isEmpty()) {
                    Text(
                        "还没有模型。先「获取可用模型」，或点上面「+ 添加模型」手动填 ID。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                configs.forEach { config ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (config.id == model) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                            )
                            .clickable { model = config.id }
                            .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (config.id == model) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (config.id == model) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(config.displayName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Text(
                                buildString {
                                    append(config.id)
                                    append(" · ").append(config.contextWindow / 1000).append("k")
                                    if (config.vision) append(" · 多模态")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { editing = config }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "模型设置",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        IconButton(
                            onClick = {
                                configs = configs.filterNot { it.id == config.id }
                                if (model == config.id) model = configs.firstOrNull()?.id.orEmpty()
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "删除模型",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        ModelProvider(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            baseUrl = baseUrl.trim().trimEnd('/'),
                            apiKey = apiKey.trim(),
                            model = model.ifBlank { configs.firstOrNull()?.id.orEmpty() },
                            extraHeaders = headers.trim(),
                            models = configs,
                            createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                        ),
                    )
                },
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && configs.isNotEmpty(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (adding) {
        ModelConfigDialog(
            initial = null,
            onDismiss = { adding = false },
            onSave = { config ->
                configs = (configs + config).distinctBy { it.id }
                if (model.isBlank()) model = config.id
                adding = false
            },
        )
    }
    editing?.let { config ->
        ModelConfigDialog(
            initial = config,
            onDismiss = { editing = null },
            onSave = { updated ->
                configs = configs.map { if (it.id == config.id) updated else it }
                if (model == config.id) model = updated.id
                editing = null
            },
        )
    }
}

@Composable
private fun ModelConfigDialog(
    initial: ModelConfig?,
    onDismiss: () -> Unit,
    onSave: (ModelConfig) -> Unit,
) {
    var id by remember { mutableStateOf(initial?.id ?: "") }
    var displayName by remember { mutableStateOf(initial?.name ?: "") }
    var contextWindow by remember { mutableStateOf(initial?.contextWindow ?: 128_000) }
    var vision by remember { mutableStateOf(initial?.vision ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加模型" else "模型设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("模型 ID") },
                    placeholder = { Text("deepseek-chat") },
                    singleLine = true,
                    enabled = initial == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名称（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField(
                    label = "上下文长度（tokens）",
                    value = contextWindow,
                    range = 2_000..2_000_000,
                    onValueChange = { contextWindow = it },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("多模态", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "开启后才能给这个模型发图片",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = vision, onCheckedChange = { vision = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(ModelConfig(id.trim(), displayName.trim(), vision, contextWindow)) },
                enabled = id.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Uri,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ModelSelector(model: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = model,
            onValueChange = onSelect,
            label = { Text("模型") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        Icons.Rounded.ArrowDropDown,
                        contentDescription = "选择预设模型",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MODEL_PRESETS.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset) },
                    onClick = {
                        onSelect(preset)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { text ->
                val digits = text.filter { it.isDigit() }.take(6)
                if (digits.isEmpty()) {
                    onValueChange(range.first)
                } else {
                    onValueChange(digits.toInt().coerceIn(range))
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(124.dp),
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ProbeLine(probe: ProbeState) {
    when (probe) {
        ProbeState.Idle -> Unit
        ProbeState.Loading -> Text(
            "正在连接…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is ProbeState.Success -> Text(
            "✓ ${probe.message}",
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.Success,
        )
        is ProbeState.Failure -> Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Rounded.ErrorOutline,
                null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                probe.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun compactTokens(value: Int): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}M"
    value >= 1_000 -> "${value / 1_000}K"
    else -> value.toString()
}