package com.luoty.agent.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 单个模型：可单独设置显示名、上下文长度、是否多模态 */
data class ModelConfig(
    val id: String,
    val name: String = "",
    val vision: Boolean = true,
    val contextWindow: Int = 128_000,
) {
    val displayName: String get() = name.ifBlank { id }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("vision", vision)
        put("contextWindow", contextWindow)
    }

    companion object {
        fun fromJson(o: JSONObject): ModelConfig = ModelConfig(
            id = o.optString("id"),
            name = o.optString("name", ""),
            vision = o.optBoolean("vision", true),
            contextWindow = o.optInt("contextWindow", 128_000),
        )
    }
}

/** 一个模型提供方：名称 + 接口地址 + 密钥 + 模型目录 + 额外请求头 */
data class ModelProvider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val extraHeaders: String = "",
    val models: List<ModelConfig> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val host: String
        get() = baseUrl.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")

    val activeModel: ModelConfig?
        get() = models.firstOrNull { it.id == model } ?: models.firstOrNull()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("baseUrl", baseUrl)
        put("apiKey", apiKey)
        put("model", model)
        put("extraHeaders", extraHeaders)
        put("models", JSONArray(models.map { it.toJson() }))
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): ModelProvider = ModelProvider(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name", "未命名"),
            baseUrl = o.optString("baseUrl", ""),
            apiKey = o.optString("apiKey", ""),
            model = o.optString("model", ""),
            extraHeaders = o.optString("extraHeaders", ""),
            models = o.optJSONArray("models")?.let { arr ->
                (0 until arr.length()).mapNotNull { index ->
                    val item = arr.opt(index)
                    when (item) {
                        is JSONObject -> ModelConfig.fromJson(item).takeIf { it.id.isNotBlank() }
                        is String -> item.takeIf { it.isNotBlank() }?.let { ModelConfig(id = it) }
                        else -> null
                    }
                }
            }.orEmpty(),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

class ProviderStore(context: Context) {
    private val prefs = context.getSharedPreferences("luoty_agent_providers", Context.MODE_PRIVATE)

    fun load(): List<ModelProvider> {
        val raw = prefs.getString("providers", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { ModelProvider.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun save(providers: List<ModelProvider>) {
        prefs.edit().putString("providers", JSONArray(providers.map { it.toJson() }).toString()).apply()
    }

    fun activeId(): String? = prefs.getString("active_id", null)

    fun saveActiveId(id: String?) {
        prefs.edit().putString("active_id", id).apply()
    }
}

/** 内置预设：OpenCode Go（需要会话头，流式与工具调用已验证可用） */
fun openCodeGoPreset(apiKey: String = ""): ModelProvider = ModelProvider(
    name = "OpenCode Go",
    baseUrl = "https://opencode.ai/inference/go/v1",
    apiKey = apiKey,
    model = "deepseek-v4.1-flash",
    extraHeaders = "x-opencode-session: \${session}",
    models = listOf(
        ModelConfig("deepseek-v4.1-flash", "DeepSeek V4.1 Flash", vision = false, contextWindow = 200_000),
    ),
)

/** 内置预设：DeepSeek 官方 */
fun deepSeekPreset(apiKey: String = ""): ModelProvider = ModelProvider(
    name = "DeepSeek",
    baseUrl = "https://api.deepseek.com",
    apiKey = apiKey,
    model = "deepseek-chat",
    models = listOf(ModelConfig("deepseek-chat", "DeepSeek Chat", vision = false, contextWindow = 128_000)),
)

/** 添加提供方时可选的一批常见接口地址 */
data class ProviderPreset(
    val name: String,
    val baseUrl: String,
    val model: String,
    val extraHeaders: String = "",
    val contextWindow: Int = 128_000,
    val vision: Boolean = true,
)

val PROVIDER_PRESETS: List<ProviderPreset> = listOf(
    ProviderPreset("DeepSeek", "https://api.deepseek.com", "deepseek-chat", contextWindow = 128_000, vision = false),
    ProviderPreset(
        "OpenCode Go",
        "https://opencode.ai/inference/go/v1",
        "deepseek-v4.1-flash",
        extraHeaders = "x-opencode-session: \${session}",
        contextWindow = 200_000,
    ),
    ProviderPreset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", contextWindow = 128_000),
    ProviderPreset("Kimi", "https://api.moonshot.cn/v1", "kimi-k2-0905-preview", contextWindow = 256_000),
    ProviderPreset("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4.6", contextWindow = 128_000),
    ProviderPreset(
        "阿里 Qwen",
        "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "qwen3-coder-plus",
        contextWindow = 256_000,
    ),
    ProviderPreset("SiliconFlow", "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V3", contextWindow = 128_000),
    ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1", "deepseek/deepseek-chat", contextWindow = 128_000),
)
