package com.luoty.agent.data

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    val baseUrl: String = "https://api.deepseek.com",
    val apiKey: String = "",
    val model: String = "deepseek-chat",
    val temperature: Float = 0.3f,
    val maxTokens: Int = 256_000,
    val maxSteps: Int = 24,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val autoTitle: Boolean = true,
    val showReasoning: Boolean = true,
    val permissionMode: String = PERM_SAFE,
    val autoCompact: Boolean = true,
    val compactThreshold: Int = 100_000,
    val compactKeepRecent: Int = 6,
    val extraHeaders: String = "",
    val providerName: String = "",
    val visionEnabled: Boolean = true,
    val contextWindow: Int = 1_000_000,
    val reasoningEffort: String = "default",
    val teamMode: Boolean = false,
    val busyBehavior: String = BUSY_QUEUE,
    val searchProvider: String = "none",
    val searchApiKey: String = "",
) {
    val isReady: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
    /** 粗略估算可容纳的字符数（按 3 字符/token 计） */
    val contextChars: Int get() = contextWindow * 3
}

const val PERM_FULL = "full"
const val PERM_READONLY = "readonly"
const val PERM_CONFIRM = "confirm"
const val PERM_SAFE = "safe"

const val BUSY_QUEUE = "queue"
const val BUSY_STEER = "steer"
const val BUSY_INTERRUPT = "interrupt"

fun busyBehaviorLabel(mode: String): String = when (mode) {
    BUSY_STEER -> "插入会话"
    BUSY_INTERRUPT -> "打断并发送"
    else -> "排队发送"
}

fun permissionLabel(mode: String): String = when (mode) {
    PERM_READONLY -> "只读"
    PERM_CONFIRM -> "每次确认"
    else -> "完全权限"
}

val WRITE_TOOLS = setOf("write_file", "edit_file", "delete_path", "run_command")

val MODEL_PRESETS = listOf(
    "deepseek-chat",
    "deepseek-reasoner",
    "deepseek-coder",
    "gpt-4o-mini",
    "claude-sonnet-4-5",
    "qwen3-coder-plus",
    "glm-4.6",
    "kimi-k2-0905-preview",
)

/** 内置系统上下文（只读，不能在设置里修改） */
const val CORE_SYSTEM_PROMPT = """你是洛天依（Luo Tianyi），一位来自 Vsinger 的虚拟歌手，现在作为用户的私人 AI 编码助手工作。

说话方式：轻快、礼貌、带一点点可爱的语气（比如「好的呀」「交给我吧」「我看看哦」），但技术内容必须准确、克制，不要卖萌过头，也不要每句都加语气词。用户用中文就用中文回答。

做事规则：
- 需要了解现状时，先 list_files / search_files / read_file，不要凭空猜测文件内容。
- 需要落盘修改时，使用 write_file / edit_file，不要只在聊天里贴代码。
- 改动前先读文件；优先 edit_file 做小范围精确修改，避免整文件重写。
- 每次工具调用后都要看返回结果；报错就修正后重试，不要无视失败。
- 任务较大时拆成多个小步骤，边做边验证。
- 拿不准用户意图、或需要用户在几个方案里选一个时，调用 ask_user 提问，不要自己乱猜。
- 回复使用简体中文，代码块标注语言；解释保持简洁，重点说清改了什么、为什么。"""

/** 用户可编辑的附加指令，默认为空 */
const val DEFAULT_SYSTEM_PROMPT = ""

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("luoty_agent_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val settings = AppSettings(
        baseUrl = prefs.getString(KEY_BASE_URL, null) ?: "https://api.deepseek.com",
        apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
        model = prefs.getString(KEY_MODEL, null) ?: "deepseek-chat",
        temperature = prefs.getFloat(KEY_TEMPERATURE, 0.3f),
        maxTokens = prefs.getInt(KEY_MAX_TOKENS, 256_000),
        maxSteps = prefs.getInt(KEY_MAX_STEPS, 24),
        systemPrompt = prefs.getString(KEY_SYSTEM_PROMPT, null)
            ?.takeUnless { it.startsWith("你是 LuoTy Agent") }
            ?: DEFAULT_SYSTEM_PROMPT,
        autoTitle = prefs.getBoolean(KEY_AUTO_TITLE, true),
        showReasoning = prefs.getBoolean(KEY_SHOW_REASONING, true),
        permissionMode = (prefs.getString(KEY_PERMISSION, PERM_SAFE) ?: PERM_SAFE)
            .let { if (it == PERM_CONFIRM) PERM_SAFE else it },
        autoCompact = prefs.getBoolean(KEY_AUTO_COMPACT, true),
        compactThreshold = prefs.getInt(KEY_COMPACT_THRESHOLD, 100_000),
        compactKeepRecent = prefs.getInt(KEY_COMPACT_KEEP, 6),
        extraHeaders = prefs.getString(KEY_EXTRA_HEADERS, "") ?: "",
        visionEnabled = prefs.getBoolean(KEY_VISION, true),
        contextWindow = prefs.getInt(KEY_CONTEXT_WINDOW, 1_000_000),
        reasoningEffort = prefs.getString(KEY_REASONING, "default") ?: "default",
        teamMode = prefs.getBoolean(KEY_TEAM, false),
        busyBehavior = prefs.getString(KEY_BUSY, BUSY_QUEUE) ?: BUSY_QUEUE,
        providerName = prefs.getString(KEY_PROVIDER_NAME, "") ?: "",
        searchProvider = prefs.getString(KEY_SEARCH_PROVIDER, "none") ?: "none",
        searchApiKey = prefs.getString(KEY_SEARCH_KEY, "") ?: "",
        )
        if (!prefs.getBoolean("ctx_split", false)) {
            prefs.edit().putBoolean("ctx_split", true).apply()
            return settings.copy(maxTokens = 256_000, contextWindow = 1_000_000)
        }
        return settings
    }

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_BASE_URL, settings.baseUrl)
            .putString(KEY_API_KEY, settings.apiKey)
            .putString(KEY_MODEL, settings.model)
            .putFloat(KEY_TEMPERATURE, settings.temperature)
            .putInt(KEY_MAX_TOKENS, settings.maxTokens)
            .putInt(KEY_MAX_STEPS, settings.maxSteps)
            .putString(KEY_SYSTEM_PROMPT, settings.systemPrompt)
            .putBoolean(KEY_AUTO_TITLE, settings.autoTitle)
            .putBoolean(KEY_SHOW_REASONING, settings.showReasoning)
            .putString(KEY_PERMISSION, settings.permissionMode)
            .putBoolean(KEY_AUTO_COMPACT, settings.autoCompact)
            .putInt(KEY_COMPACT_THRESHOLD, settings.compactThreshold)
            .putInt(KEY_COMPACT_KEEP, settings.compactKeepRecent)
            .putString(KEY_EXTRA_HEADERS, settings.extraHeaders)
            .putString(KEY_PROVIDER_NAME, settings.providerName)
            .putBoolean(KEY_VISION, settings.visionEnabled)
            .putInt(KEY_CONTEXT_WINDOW, settings.contextWindow)
            .putString(KEY_REASONING, settings.reasoningEffort)
            .putBoolean(KEY_TEAM, settings.teamMode)
            .putString(KEY_BUSY, settings.busyBehavior)
            .putString(KEY_SEARCH_PROVIDER, settings.searchProvider)
            .putString(KEY_SEARCH_KEY, settings.searchApiKey)
            .apply()
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_MAX_TOKENS = "max_tokens"
        const val KEY_MAX_STEPS = "max_steps"
        const val KEY_SYSTEM_PROMPT = "system_prompt"
        const val KEY_AUTO_TITLE = "auto_title"
        const val KEY_SHOW_REASONING = "show_reasoning"
        const val KEY_PERMISSION = "permission"
        const val KEY_AUTO_COMPACT = "auto_compact"
        const val KEY_COMPACT_THRESHOLD = "compact_threshold"
        const val KEY_COMPACT_KEEP = "compact_keep"
        const val KEY_EXTRA_HEADERS = "extra_headers"
        const val KEY_PROVIDER_NAME = "provider_name"
        const val KEY_VISION = "vision_enabled"
        const val KEY_CONTEXT_WINDOW = "context_window"
        const val KEY_REASONING = "reasoning_effort"
        const val KEY_TEAM = "team_mode"
        const val KEY_BUSY = "busy_behavior"
        const val KEY_SEARCH_PROVIDER = "search_provider"
        const val KEY_SEARCH_KEY = "search_key"
    }
}
