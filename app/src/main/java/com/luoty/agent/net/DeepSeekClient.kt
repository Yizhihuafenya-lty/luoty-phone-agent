package com.luoty.agent.net

import com.luoty.agent.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class ApiToolCall(val id: String, val name: String, val arguments: String)

/** 多模态消息片段：text 或 image_url */
data class ApiPart(
    val type: String,
    val text: String? = null,
    val imageUrl: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        when (type) {
            "text" -> put("text", text.orEmpty())
            "image_url" -> put("image_url", JSONObject().put("url", imageUrl.orEmpty()))
        }
    }
}

data class ApiMessage(
    val role: String,
    val content: String? = null,
    val parts: List<ApiPart> = emptyList(),
    val toolCalls: List<ApiToolCall> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("role", role)
        when {
            parts.isNotEmpty() -> put("content", JSONArray(parts.map { it.toJson() }))
            content != null -> put("content", content)
        }
        if (toolCalls.isNotEmpty()) {
            put(
                "tool_calls",
                JSONArray(toolCalls.map { c ->
                    JSONObject()
                        .put("id", c.id)
                        .put("type", "function")
                        .put("function", JSONObject().put("name", c.name).put("arguments", c.arguments))
                }),
            )
        }
        toolCallId?.let { put("tool_call_id", it) }
        name?.let { put("name", it) }
    }
}

data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JSONObject,
    val required: List<String> = emptyList(),
) {
    fun toJson(): JSONObject {
        val params = JSONObject(parameters.toString()).put("required", JSONArray(required))
        return JSONObject().apply {
            put("type", "function")
            put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put("parameters", params),
            )
        }
    }
}

data class StreamResult(
    val content: String,
    val reasoning: String,
    val toolCalls: List<ApiToolCall>,
    val finishReason: String?,
    val promptTokens: Int,
    val completionTokens: Int,
    val cachedTokens: Int = 0,
)

class ApiException(message: String, val code: Int? = null, cause: Throwable? = null) :
    Exception(message, cause)

class DeepSeekClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val sessionId: String = UUID.randomUUID().toString()

    /**
     * 统一加鉴权与自定义请求头。extraHeaders 每行一个 "Name: value"，
     * 支持 ${session} 占位符（部分网关要求会话标识，例如 OpenCode Go）。
     */
    private fun Request.Builder.applyProviderHeaders(settings: AppSettings): Request.Builder = apply {
        addHeader("Authorization", "Bearer ${settings.apiKey.trim()}")
        settings.extraHeaders.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || !line.contains(":")) return@forEach
            val name = line.substringBefore(":").trim()
            val value = line.substringAfter(":").trim().replace("\${session}", sessionId)
            if (name.isNotEmpty()) addHeader(name, value)
        }
    }

    fun endpoint(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/chat/completions"
        }
    }

    /**
     * 流式请求一次 chat completion。回调在 IO 线程执行，逐段吐出文本。
     * 取消协程会立即 cancel 掉底层 HTTP 请求（不必等读超时）。
     */
    suspend fun stream(
        settings: AppSettings,
        messages: List<ApiMessage>,
        tools: List<ToolSpec>,
        onText: (String) -> Unit,
        onReasoning: (String) -> Unit,
    ): StreamResult = withContext(Dispatchers.IO) {
        try {
            return@withContext streamOnce(settings, messages, tools, true, true, onText, onReasoning)
        } catch (e: ApiException) {
            // 部分 OpenAI 兼容服务不认识 stream_options / reasoning_effort，逐个去掉再试
            if (e.code == 400) {
                return@withContext streamOnce(settings, messages, tools, false, true, onText, onReasoning)
            }
            throw e
        }
    }

    private suspend fun streamOnce(
        settings: AppSettings,
        messages: List<ApiMessage>,
        tools: List<ToolSpec>,
        includeUsage: Boolean,
        includeReasoning: Boolean,
        onText: (String) -> Unit,
        onReasoning: (String) -> Unit,
    ): StreamResult {
        val body = buildBody(settings, messages, tools, includeUsage, includeReasoning)
        val request = Request.Builder()
            .url(endpoint(settings.baseUrl))
            .applyProviderHeaders(settings)
            .addHeader("Accept", "text/event-stream")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        val call = client.newCall(request)
        val job = coroutineContext[Job]
        val cancelHandle = job?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }

        try {
            call.execute().use { response ->
                coroutineContext.ensureActive()
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    throw ApiException(friendlyError(response.code, err), response.code)
                }
                val source = response.body?.source()
                    ?: throw ApiException("服务端返回了空响应")

                val content = StringBuilder()
                val reasoning = StringBuilder()
                val toolAcc = linkedMapOf<Int, ToolCallBuilder>()
                var finishReason: String? = null
                var promptTokens = 0
                var completionTokens = 0
                var cachedTokens = 0

                while (true) {
                    coroutineContext.ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) continue
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break

                    val chunk = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    chunk.optJSONObject("error")?.let {
                        throw ApiException(it.optString("message", "上游返回错误"))
                    }

                    chunk.optJSONObject("usage")?.let { u ->
                        promptTokens = u.optInt("prompt_tokens", promptTokens)
                        completionTokens = u.optInt("completion_tokens", completionTokens)
                        // OpenAI 风格与 DeepSeek 风格都兼容
                        cachedTokens = u.optJSONObject("prompt_tokens_details")
                            ?.optInt("cached_tokens", 0)
                            ?: u.optInt("prompt_cache_hit_tokens", 0)
                    }

                    val choices = chunk.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val choice = choices.optJSONObject(0) ?: continue
                    val delta = choice.optJSONObject("delta")
                    if (delta != null) {
                        val text = delta.optString("content", "")
                        if (text.isNotEmpty()) {
                            content.append(text)
                            onText(text)
                        }
                        val think = delta.optString("reasoning_content", "")
                            .ifEmpty { delta.optString("reasoning", "") }
                        if (think.isNotEmpty()) {
                            reasoning.append(think)
                            onReasoning(think)
                        }
                        delta.optJSONArray("tool_calls")?.let { arr ->
                            for (i in 0 until arr.length()) {
                                val tc = arr.optJSONObject(i) ?: continue
                                val index = tc.optInt("index", i)
                                val builder = toolAcc.getOrPut(index) { ToolCallBuilder(index) }
                                tc.optString("id", "").takeIf { it.isNotEmpty() }?.let { builder.id = it }
                                val fn = tc.optJSONObject("function")
                                if (fn != null) {
                                    fn.optString("name", "").takeIf { it.isNotEmpty() }?.let {
                                        builder.name = if (builder.name.isEmpty()) it else builder.name + it
                                    }
                                    val argsPiece = fn.optString("arguments", "")
                                    if (argsPiece.isNotEmpty()) builder.arguments.append(argsPiece)
                                }
                            }
                        }
                    }
                    choice.optString("finish_reason", "").takeIf { it.isNotBlank() && it != "null" }
                        ?.let { finishReason = it }
                }

                val toolCalls = toolAcc.values
                    .sortedBy { it.index }
                    .filter { it.name.isNotBlank() }
                    .map {
                        ApiToolCall(
                            id = it.id.ifBlank { "call_${System.nanoTime()}_${it.index}" },
                            name = it.name,
                            arguments = it.arguments.toString().ifBlank { "{}" },
                        )
                    }

                return StreamResult(
                    content = content.toString(),
                    reasoning = reasoning.toString(),
                    toolCalls = toolCalls,
                    finishReason = finishReason,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    cachedTokens = cachedTokens,
                )
            }
        } catch (e: IOException) {
            if (coroutineContext[Job]?.isActive == false) throw e
            throw ApiException("网络请求失败：${e.message ?: "连接被中断"}", cause = e)
        } finally {
            cancelHandle?.dispose()
        }
    }

    /** 设置页的连通性测试：优先 /models，个别服务不支持时退回一次极小的对话请求 */
    suspend fun testConnection(settings: AppSettings): String = withContext(Dispatchers.IO) {
        probeModels(settings)?.let { return@withContext it }

        val body = JSONObject()
            .put("model", settings.model)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", "ping")),
            )
            .put("max_tokens", 1)
            .put("stream", false)
        val post = Request.Builder()
            .url(endpoint(settings.baseUrl))
            .applyProviderHeaders(settings)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(post).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw ApiException(friendlyError(resp.code, resp.body?.string().orEmpty()), resp.code)
            }
        }
        "连接成功（对话接口可用）"
    }

    /** 返回 null 表示该服务没有 /models 接口，交给对话探测 */
    private fun probeModels(settings: AppSettings): String? {
        val base = settings.baseUrl.trim().trimEnd('/')
        val req = Request.Builder()
            .url("$base/models")
            .applyProviderHeaders(settings)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                val text = resp.body?.string().orEmpty()
                val names = runCatching {
                    val arr = JSONObject(text).optJSONArray("data") ?: JSONArray()
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("id") }
                }.getOrDefault(emptyList())
                return if (names.isEmpty()) {
                    "连接成功（服务可用）"
                } else {
                    "连接成功，可用模型：" + names.take(6).joinToString("、")
                }
            }
            if (resp.code != 404 && resp.code != 405) {
                throw ApiException(friendlyError(resp.code, resp.body?.string().orEmpty()), resp.code)
            }
        }
        return null
    }

    /** 拉取提供方的模型目录（GET {base}/models） */
    suspend fun fetchModels(settings: AppSettings): List<String> = withContext(Dispatchers.IO) {
        val base = settings.baseUrl.trim().trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) {
            base.removeSuffix("/chat/completions") + "/models"
        } else {
            "$base/models"
        }
        val request = Request.Builder()
            .url(url)
            .applyProviderHeaders(settings)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(friendlyError(response.code, text), response.code)
            }
            val arr = JSONObject(text).optJSONArray("data") ?: JSONArray()
            (0 until arr.length())
                .mapNotNull { arr.optJSONObject(it)?.optString("id")?.takeIf { id -> id.isNotBlank() } }
        }
    }

    /** 非流式补全：用于上下文压缩、标题生成等内部任务 */
    suspend fun complete(
        settings: AppSettings,
        messages: List<ApiMessage>,
        maxTokens: Int = 1200,
        temperature: Double = 0.2,
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", settings.model)
            .put("messages", JSONArray(messages.map { it.toJson() }))
            .put("stream", false)
            .put("temperature", temperature)
            .put("max_tokens", maxTokens)
        val request = Request.Builder()
            .url(endpoint(settings.baseUrl))
            .applyProviderHeaders(settings)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(friendlyError(response.code, text), response.code)
            }
            val json = runCatching { JSONObject(text) }.getOrElse {
                throw ApiException("接口返回了非 JSON 内容：${text.take(200)}")
            }
            json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
        }
    }

    private fun buildBody(
        settings: AppSettings,
        messages: List<ApiMessage>,
        tools: List<ToolSpec>,
        includeUsage: Boolean,
        includeReasoning: Boolean,
    ): String {
        val json = JSONObject()
        json.put("model", settings.model)
        json.put("messages", JSONArray(messages.map { it.toJson() }))
        json.put("stream", true)
        if (settings.temperature in 0f..2f) {
            json.put("temperature", settings.temperature.toDouble())
        }
        if (settings.maxTokens > 0) json.put("max_tokens", settings.maxTokens)
        if (includeReasoning && settings.reasoningEffort != "default") {
            json.put("reasoning_effort", settings.reasoningEffort)
        }
        if (tools.isNotEmpty()) {
            json.put("tools", JSONArray(tools.map { it.toJson() }))
            json.put("tool_choice", "auto")
        }
        if (includeUsage) {
            json.put("stream_options", JSONObject().put("include_usage", true))
        }
        return json.toString()
    }

    private fun friendlyError(code: Int, body: String): String {
        val detail = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("").ifBlank {
            runCatching { JSONObject(body).optString("message") }.getOrDefault("").ifBlank {
                body.take(280)
            }
        }
        val base = when (code) {
            400 -> "请求参数有问题（400）"
            401 -> "API Key 无效或没有权限（401）"
            402 -> "账户余额不足（402）"
            403 -> "请求被拒绝（403）"
            404 -> "接口地址不存在（404），请检查 Base URL"
            422 -> "请求参数不合法（422）"
            429 -> "请求太频繁或超出限额（429）"
            in 500..599 -> "服务端错误（$code）"
            else -> "请求失败（$code）"
        }
        return if (detail.isBlank()) base else "$base：$detail"
    }

    private class ToolCallBuilder(val index: Int) {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
