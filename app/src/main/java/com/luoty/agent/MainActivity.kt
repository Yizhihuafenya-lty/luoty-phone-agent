package com.luoty.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.luoty.agent.ui.AgentViewModel
import com.luoty.agent.ui.AppRoot
import com.luoty.agent.ui.theme.LuoAgentTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: AgentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            LuoAgentTheme {
                AppRoot(vm)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val viewModel = vm
        lifecycleScope.launch { viewModel.flush() }
    }
}
