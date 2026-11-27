package com.luoty.agent

import android.app.Application
import android.content.Context
import com.luoty.agent.agent.AgentEngine
import com.luoty.agent.agent.ToolRegistry
import com.luoty.agent.data.AttachmentStore
import com.luoty.agent.data.ProviderStore
import com.luoty.agent.data.SessionRepository
import com.luoty.agent.data.SettingsStore
import com.luoty.agent.net.DeepSeekClient

class AppContainer(context: Context) {
    val settingsStore = SettingsStore(context)
    val providerStore = ProviderStore(context)
    val serverStore = com.luoty.agent.data.ServerStore(context)
    val projectStore = com.luoty.agent.data.ProjectStore(context)
    val repo = SessionRepository(context)
    val tools = ToolRegistry(context)
    val attachments = AttachmentStore(context)
    val client = DeepSeekClient()
    val engine = AgentEngine(repo, client, tools, attachments, serverStore)
}

class LuoAgentApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
