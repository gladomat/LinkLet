package com.gladomat.linklet.ui

import android.os.Bundle
import android.net.Uri
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gladomat.linklet.ui.screens.NoteListRoute
import com.gladomat.linklet.ui.screens.graph.GraphRoute
import com.gladomat.linklet.ui.screens.note.NoteViewRoute
import com.gladomat.linklet.ui.screens.trash.TrashRoute
import com.gladomat.linklet.ui.screens.noteedit.NoteEditRoute
import com.gladomat.linklet.ui.screens.settings.SettingsRoute
import com.gladomat.linklet.ui.screens.settings.SyncIgnoreEditorRoute
import com.gladomat.linklet.ui.screens.settings.WebDavSettingsRoute
import com.gladomat.linklet.ui.screens.sync.SyncStatusRoute
import com.gladomat.linklet.ui.theme.LinkLetAppTheme
import com.gladomat.linklet.viewmodel.note.NoteViewViewModel
import com.gladomat.linklet.viewmodel.noteedit.NoteEditViewModel
import com.gladomat.linklet.data.sync.SyncStatusNavigation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val navTargetFlow = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            LinkLetAppTheme {
                val navController = rememberNavController()
                val navTarget by navTargetFlow.collectAsStateWithLifecycle()
                LaunchedEffect(navTarget) {
                    if (navTarget == SyncStatusNavigation.NAV_TARGET_SYNC_STATUS) {
                        navController.navigate(Routes.SYNC_STATUS) {
                            launchSingleTop = true
                        }
                        navTargetFlow.value = null
                    }
                }
                Surface(color = MaterialTheme.colorScheme.background) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.NOTE_LIST,
                    ) {
                        composable(route = Routes.NOTE_LIST) {
                            NoteListRoute(
                                onOpenNote = { path ->
                                    navController.navigate("${Routes.NOTE_VIEW}/${Uri.encode(path)}") {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenSettings = {
                                    navController.navigate(Routes.SETTINGS)
                                },
                                onOpenTrash = {
                                    navController.navigate(Routes.TRASH)
                                },
                                onCreateNote = {
                                    navController.navigate("${Routes.NOTE_EDIT}/${Uri.encode(Routes.NEW_NOTE_PATH)}") {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenSyncStatus = {
                                    navController.navigate(Routes.SYNC_STATUS) {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenGraph = {
                                    navController.navigate(Routes.GRAPH)
                                },
                            )
                        }

                        composable(
                            route = "${Routes.NOTE_VIEW}/{${NoteViewViewModel.NoteArgs.NOTE_PATH}}",
                            arguments = listOf(
                                navArgument(NoteViewViewModel.NoteArgs.NOTE_PATH) {
                                    type = NavType.StringType
                                },
                            ),
                        ) { backStackEntry ->
                            NoteViewRoute(
                                onEditNote = { path ->
                                    navController.navigate("${Routes.NOTE_EDIT}/${Uri.encode(path)}")
                                },
                                onNavigateHome = {
                                    navController.popBackStack(Routes.NOTE_LIST, false)
                                },
                                onExitToList = {
                                    navController.popBackStack()
                                },
                                onCreateNote = {
                                    navController.navigate("${Routes.NOTE_EDIT}/${Uri.encode(Routes.NEW_NOTE_PATH)}") {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenGraph = { path ->
                                    navController.navigate("${Routes.GRAPH}?${Routes.GRAPH_CENTER_ARG}=${Uri.encode(path)}")
                                },
                                savedStateHandle = backStackEntry.savedStateHandle,
                            )
                        }

                        composable(
                            route = "${Routes.NOTE_EDIT}/{${Routes.NOTE_EDIT_PATH}}",
                            arguments = listOf(
                                navArgument(Routes.NOTE_EDIT_PATH) {
                                    type = NavType.StringType
                                },
                            ),
                        ) {
                            NoteEditRoute(
                                onDone = { savedPath ->
                                    // Signal NoteView to refresh before popping back
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set(REFRESH_NOTE_KEY, true)
                                    navController.popBackStack()
                                    navController.navigate("${Routes.NOTE_VIEW}/${Uri.encode(savedPath)}") {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                            )
                        }

                        composable(route = Routes.SETTINGS) {
                            SettingsRoute(
                                onNavigateBack = { navController.popBackStack() },
                                onOpenWebDavSettings = {
                                    navController.navigate(Routes.WEB_DAV_SETTINGS)
                                },
                            )
                        }

                        composable(route = Routes.WEB_DAV_SETTINGS) {
                            WebDavSettingsRoute(
                                onNavigateBack = { navController.popBackStack() },
                                onOpenSyncIgnoreEditor = {
                                    navController.navigate(Routes.SYNC_IGNORE_EDITOR)
                                },
                            )
                        }

                        composable(route = Routes.SYNC_IGNORE_EDITOR) {
                            SyncIgnoreEditorRoute(
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }

                        composable(route = Routes.TRASH) {
                            TrashRoute(
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }

                        composable(route = Routes.SYNC_STATUS) {
                            SyncStatusRoute(
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }

                        composable(
                            route = "${Routes.GRAPH}?${Routes.GRAPH_CENTER_ARG}={${Routes.GRAPH_CENTER_ARG}}",
                            arguments = listOf(
                                navArgument(Routes.GRAPH_CENTER_ARG) {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                            ),
                        ) {
                            GraphRoute(
                                onOpenNote = { path ->
                                    navController.navigate("${Routes.NOTE_VIEW}/${Uri.encode(path)}") {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        navTargetFlow.value = intent?.getStringExtra(SyncStatusNavigation.EXTRA_NAV_TARGET)
    }
}

private object Routes {
    const val NOTE_LIST = "note_list"
    const val NOTE_VIEW = "note_view"
    const val NOTE_EDIT = "note_edit"
    const val NOTE_EDIT_PATH = NoteEditViewModel.NoteArgs.NOTE_PATH
    const val NEW_NOTE_PATH = NoteEditViewModel.NEW_NOTE_PATH
    const val SETTINGS = "settings"
    const val WEB_DAV_SETTINGS = "webdav_settings"
    const val SYNC_IGNORE_EDITOR = "sync_ignore_editor"
    const val TRASH = "trash"
    const val SYNC_STATUS = "sync_status"
    const val GRAPH = "graph"
    const val GRAPH_CENTER_ARG = "center"
}

const val REFRESH_NOTE_KEY = "refresh_note"
