package com.luoty.agent.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 一个项目 = 一个共享工作文件夹，项目下所有会话共用 */
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Project = Project(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name", "未命名项目"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

class ProjectStore(context: Context) {
    private val prefs = context.getSharedPreferences("luoty_agent_projects", Context.MODE_PRIVATE)

    fun load(): List<Project> {
        val raw = prefs.getString("projects", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Project.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun save(projects: List<Project>) {
        prefs.edit().putString("projects", JSONArray(projects.map { it.toJson() }).toString()).apply()
    }

    fun find(id: String): Project? = load().firstOrNull { it.id == id }
}
