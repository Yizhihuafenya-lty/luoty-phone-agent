package com.luoty.agent.agent

import com.luoty.agent.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 联网搜索：优先用配置好的搜索 API（Tavily / Brave / Serper），
 * 没配或调用失败时退回 DuckDuckGo HTML 抓取，保证离线/零配置也能用。
 */
class WebSearch(private val settings: () -> AppSettings) {

    data class Hit(val title: String, val url: String, val snippet: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, limit: Int): List<Hit> = withContext(Dispatchers.IO) {
        val config = settings()
        val count = limit.coerceIn(1, 8)
        val api = when (config.searchProvider) {
            "tavily", "brave", "serper" -> if (config.searchApiKey.isNotBlank()) {
                runCatching { viaApi(config, query, count) }.getOrNull().orEmpty()
            } else {
                emptyList()
            }
            else -> emptyList()
        }
        if (api.isNotEmpty()) return@withContext api
        runCatching { viaDuckDuckGo(query, count) }.getOrDefault(emptyList())
    }

    private fun viaApi(config: AppSettings, query: String, count: Int): List<Hit> = when (config.searchProvider) {
        "tavily" -> {
            val body = JSONObject()
                .put("api_key", config.searchApiKey)
                .put("query", query)
                .put("max_results", count)
                .put("search_depth", "basic")
            val response = postJson("https://api.tavily.com/search", body, emptyMap())
            val results = response.optJSONArray("results") ?: JSONArray()
            (0 until results.length()).mapNotNull { index ->
                val item = results.optJSONObject(index) ?: return@mapNotNull null
                Hit(
                    item.optString("title"),
                    item.optString("url"),
                    item.optString("content").take(400),
                )
            }
        }
        "brave" -> {
            val url = "https://api.search.brave.com/res/v1/web/search?q=" +
                URLEncoder.encode(query, "UTF-8") + "&count=$count"
            val response = getJson(url, mapOf("X-Subscription-Token" to config.searchApiKey, "Accept" to "application/json"))
            val results = response.optJSONObject("web")?.optJSONArray("results") ?: JSONArray()
            (0 until results.length()).mapNotNull { index ->
                val item = results.optJSONObject(index) ?: return@mapNotNull null
                Hit(
                    item.optString("title"),
                    item.optString("url"),
                    stripHtml(item.optString("description")).take(400),
                )
            }
        }
        "serper" -> {
            val body = JSONObject().put("q", query).put("num", count)
            val response = postJson("https://google.serper.dev/search", body, mapOf("X-API-KEY" to config.searchApiKey))
            val results = response.optJSONArray("organic") ?: JSONArray()
            (0 until results.length()).mapNotNull { index ->
                val item = results.optJSONObject(index) ?: return@mapNotNull null
                Hit(
                    item.optString("title"),
                    item.optString("link"),
                    item.optString("snippet").take(400),
                )
            }
        }
        else -> emptyList()
    }

    private fun viaDuckDuckGo(query: String, count: Int): List<Hit> {
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8"))
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122 Mobile Safari/537.36")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()
        val html = client.newCall(request).execute().use { it.body?.string().orEmpty() }
        val linkPattern = Regex("""<a[^>]+class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val snippetPattern = Regex("""<a[^>]+class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val snippets = snippetPattern.findAll(html).map { stripHtml(it.groupValues[1]) }.toList()
        return linkPattern.findAll(html).take(count).mapIndexed { index, match ->
            var url = match.groupValues[1]
            if (url.startsWith("//duckduckgo.com/l/")) {
                val encoded = Regex("""uddg=([^&]+)""").find(url)?.groupValues?.getOrNull(1)
                if (encoded != null) url = java.net.URLDecoder.decode(encoded, "UTF-8")
            }
            Hit(
                title = stripHtml(match.groupValues[2]),
                url = url,
                snippet = snippets.getOrElse(index) { "" }.take(400),
            )
        }.toList()
    }

    private fun postJson(url: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        val builder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        headers.forEach { (key, value) -> builder.header(key, value) }
        return client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("搜索接口 HTTP ${response.code}: ${text.take(200)}")
            JSONObject(text)
        }
    }

    private fun getJson(url: String, headers: Map<String, String>): JSONObject {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (key, value) -> builder.header(key, value) }
        return client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("搜索接口 HTTP ${response.code}: ${text.take(200)}")
            JSONObject(text)
        }
    }

    private fun stripHtml(text: String): String = text
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
