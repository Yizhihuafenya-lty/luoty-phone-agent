package com.luoty.agent.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class Role { USER, ASSISTANT }

enum class MessageStatus { STREAMING, DONE, ERROR, CANCELLED }

enum class TaskStatus { PENDING, IN_PROGRESS, DONE }

/** Agent 自己维护的任务项 */
data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val status: TaskStatus = TaskStatus.PENDING,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("status", status.name)
    }

    companion object {
        fun fromJson(o: JSONObject): TaskItem = TaskItem(
            id = o.optString("id", UUID.randomUUID().toString()),
            text = o.optString("text", ""),
            status = runCatching { TaskStatus.valueOf(o.optString("status", "PENDING")) }
                .getOrDefault(TaskStatus.PENDING),
        )
    }
}

/** 繁忙时排队等待发送的消息 */
data class QueuedMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): QueuedMessage = QueuedMessage(
            id = o.optString("id", UUID.randomUUID().toString()),
            text = o.optString("text", ""),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

/** 用户消息里的附件：图片会按多模态发送，PDF 会先抽文本再随消息发送 */
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val kind: String, // image | pdf
    val path: String,
    val name: String,
    val mime: String = "",
    val extractedText: String? = null,
    val size: Long = 0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind)
        put("path", path)
        put("name", name)
        put("mime", mime)
        extractedText?.let { put("extractedText", it) }
        put("size", size)
    }

    companion object {
        fun fromJson(o: JSONObject): Attachment = Attachment(
            id = o.optString("id", UUID.randomUUID().toString()),
            kind = o.optString("kind", "file"),
            path = o.optString("path", ""),
            name = o.optString("name", ""),
            mime = o.optString("mime", ""),
            extractedText = if (o.has("extractedText")) o.optString("extractedText") else null,
            size = o.optLong("size", 0),
        )
    }
}

enum class ToolStatus { PENDING, RUNNING, WAITING_USER, DONE, ERROR }

/** 一次工具调用（assistant 消息里可能一次返回多个） */
data class ToolInvocation(
    val id: String,
    val name: String,
    val arguments: String,
    val result: String? = null,
    val status: ToolStatus = ToolStatus.PENDING,
    val durationMs: Long = 0L,
) {
    val isError: Boolean get() = status == ToolStatus.ERROR

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("arguments", arguments)
        result?.let { put("result", it) }
        put("status", status.name)
        put("durationMs", durationMs)
    }

    companion object {
        fun fromJson(o: JSONObject): ToolInvocation = ToolInvocation(
            id = o.optString("id"),
            name = o.optString("name"),
            arguments = o.optString("arguments"),
            result = if (o.has("result")) o.optString("result") else null,
            status = runCatching { ToolStatus.valueOf(o.optString("status", "DONE")) }.getOrDefault(ToolStatus.DONE),
            durationMs = o.optLong("durationMs", 0L),
        )
    }
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String = "",
    val reasoning: String = "",
    val toolCalls: List<ToolInvocation> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val status: MessageStatus = MessageStatus.DONE,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val cachedTokens: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("role", role.name)
        put("content", content)
        put("reasoning", reasoning)
        if (toolCalls.isNotEmpty()) put("toolCalls", JSONArray(toolCalls.map { it.toJson() }))
        if (attachments.isNotEmpty()) put("attachments", JSONArray(attachments.map { it.toJson() }))
        put("status", status.name)
        error?.let { put("error", it) }
        put("createdAt", createdAt)
        put("durationMs", durationMs)
        put("promptTokens", promptTokens)
        put("completionTokens", completionTokens)
        put("cachedTokens", cachedTokens)
    }

    companion object {
        fun fromJson(o: JSONObject): ChatMessage = ChatMessage(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            role = runCatching { Role.valueOf(o.optString("role", "ASSISTANT")) }.getOrDefault(Role.ASSISTANT),
            content = o.optString("content"),
            reasoning = o.optString("reasoning"),
            toolCalls = o.optJSONArray("toolCalls")?.let { arr ->
                (0 until arr.length()).map { ToolInvocation.fromJson(arr.getJSONObject(it)) }
            }.orEmpty(),
            attachments = o.optJSONArray("attachments")?.let { arr ->
                (0 until arr.length()).map { Attachment.fromJson(arr.getJSONObject(it)) }
            }.orEmpty(),
            status = runCatching { MessageStatus.valueOf(o.optString("status", "DONE")) }
                .getOrDefault(MessageStatus.DONE),
            error = if (o.has("error")) o.optString("error") else null,
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            durationMs = o.optLong("durationMs", 0L),
            promptTokens = o.optInt("promptTokens", 0),
            completionTokens = o.optInt("completionTokens", 0),
            cachedTokens = o.optInt("cachedTokens", 0),
        )
    }
}

data class Session(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新会话",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val running: Boolean = false,
    val goal: String = "",
    val goalEnabled: Boolean = true,
    val planMode: Boolean = false,
    val summary: String = "",
    val summaryCount: Int = 0,
    val compacting: Boolean = false,
    val tasks: List<TaskItem> = emptyList(),
    val queue: List<QueuedMessage> = emptyList(),
    val activeServerId: String = "",
    val projectId: String = "",
) {
    val isBlank: Boolean get() = messages.isEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("messages", JSONArray(messages.map { it.toJson() }))
        put("goal", goal)
        put("goalEnabled", goalEnabled)
        put("planMode", planMode)
        put("summary", summary)
        put("summaryCount", summaryCount)
        if (tasks.isNotEmpty()) put("tasks", JSONArray(tasks.map { it.toJson() }))
        if (queue.isNotEmpty()) put("queue", JSONArray(queue.map { it.toJson() }))
        put("activeServerId", activeServerId)
        put("projectId", projectId)
    }

    companion object {
        fun fromJson(o: JSONObject): Session = Session(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            title = o.optString("title", "新会话"),
            messages = o.optJSONArray("messages")?.let { arr ->
                (0 until arr.length()).map { ChatMessage.fromJson(arr.getJSONObject(it)) }
            }.orEmpty(),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            goal = o.optString("goal", ""),
            goalEnabled = o.optBoolean("goalEnabled", true),
            planMode = o.optBoolean("planMode", false),
            summary = o.optString("summary", ""),
            summaryCount = o.optInt("summaryCount", 0),
            tasks = o.optJSONArray("tasks")?.let { arr ->
                (0 until arr.length()).map { TaskItem.fromJson(arr.getJSONObject(it)) }
            }.orEmpty(),
            queue = o.optJSONArray("queue")?.let { arr ->
                (0 until arr.length()).map { QueuedMessage.fromJson(arr.getJSONObject(it)) }
            }.orEmpty(),
            activeServerId = o.optString("activeServerId", ""),
            projectId = o.optString("projectId", ""),
        )
    }
}
