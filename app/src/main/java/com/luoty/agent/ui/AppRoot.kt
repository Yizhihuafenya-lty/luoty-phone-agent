package com.luoty.agent.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun AppRoot(vm: AgentViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val currentSession by vm.currentSession.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(enabled = screen != Screen.CHAT) { vm.goChat() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = screen == Screen.CHAT,
        drawerContent = {
            SessionDrawer(
                sessions = sessions,
                projects = projects,
                currentId = currentSession?.id,
                model = settings.model,
                onSelect = { id ->
                    vm.selectSession(id)
                    scope.launch { drawerState.close() }
                },
                onNewInProject = { projectId ->
                    vm.newSession(projectId)
                    scope.launch { drawerState.close() }
                },
                onCreateProject = { name -> vm.createProject(name) },
                onNew = {
                    vm.newSession()
                    scope.launch { drawerState.close() }
                },
                onRename = { id, title -> vm.renameSession(id, title) },
                onDelete = { id -> vm.deleteSession(id) },
                onPin = { id -> vm.togglePin(id) },
                onDeleteProject = { id -> vm.deleteProject(id) },
                onSettings = {
                    vm.openSettings()
                    scope.launch { drawerState.close() }
                },
                onWorkspace = {
                    vm.openWorkspace()
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when (screen) {
                    Screen.CHAT -> ChatScreen(
                        vm = vm,
                        session = currentSession,
                        settings = settings,
                        onMenu = { scope.launch { drawerState.open() } },
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        vm = vm,
                        settings = settings,
                        onBack = vm::goChat,
                    )

                    Screen.WORKSPACE -> WorkspaceScreen(
                        vm = vm,
                        onBack = vm::goChat,
                    )
                }
            }
        }
    }
}
