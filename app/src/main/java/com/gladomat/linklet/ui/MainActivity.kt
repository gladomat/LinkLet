package com.gladomat.linklet.ui

import android.os.Bundle
import android.net.Uri
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
import com.gladomat.linklet.ui.screens.note.NoteViewRoute
import com.gladomat.linklet.ui.screens.trash.TrashRoute
import com.gladomat.linklet.ui.screens.noteedit.NoteEditRoute
import com.gladomat.linklet.ui.screens.settings.SettingsRoute
import com.gladomat.linklet.ui.screens.settings.WebDavSettingsRoute
import com.gladomat.linklet.ui.theme.LinkLetAppTheme
import com.gladomat.linklet.viewmodel.note.NoteViewViewModel
import com.gladomat.linklet.viewmodel.noteedit.NoteEditViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinkLetAppTheme {
                val navController = rememberNavController()
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
                            )
                        }

                        composable(route = Routes.TRASH) {
                            TrashRoute(
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
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
    const val TRASH = "trash"
}

const val REFRESH_NOTE_KEY = "refresh_note"
