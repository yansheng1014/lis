package com.lis.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.lis.wear.model.BookMeta
import com.lis.wear.model.PlayerState
import java.io.File

object Routes {
    const val LIBRARY = "library"
    const val PLAYER = "player"
    const val CHAPTERS = "chapters"
    const val SENTENCES = "sentences"
    const val SETTINGS = "settings"
    const val PICKER = "picker"
}

@Composable
fun LisApp(
    playerState: PlayerState,
    library: List<BookMeta>,
    scanResults: List<File>,
    scanning: Boolean,
    storageGranted: Boolean,
    event: UiEvent?,
    startInPlayer: Boolean,
    onOpenBook: (BookMeta, Boolean) -> Unit,
    onImportUri: () -> Unit,
    onScan: () -> Unit,
    onImportFile: (File) -> Unit,
    onDeleteBook: (String) -> Unit,
    onGrantShizuku: () -> Unit,
    onOpenAllFilesSettings: () -> Unit,
    onConsumeEvent: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onJumpChapter: (Int) -> Unit,
    onSeekSentence: (Int) -> Unit,
    onRefreshStorage: () -> Unit,
) {
    val navController = rememberSwipeDismissableNavController()
    var showMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(event) {
        when (event) {
            is UiEvent.Message -> {
                showMessage = event.text
                onConsumeEvent()
            }
            UiEvent.ScanDone -> {
                onConsumeEvent()
            }
            null -> Unit
        }
    }

    LaunchedEffect(Unit) {
        if (startInPlayer && playerState.hasBook) {
            navController.navigate(Routes.PLAYER)
        }
    }

    // Show a transient toast-like message at the bottom of the scaffold.
    showMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            showMessage = null
        }
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
    ) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                library = library,
                playerState = playerState,
                onOpen = { onOpenBook(it, true) },
                onImportUri = onImportUri,
                onScan = onScan,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onDelete = onDeleteBook,
                onOpenPlayer = { navController.navigate(Routes.PLAYER) },
            )
        }

        composable(Routes.PLAYER) {
            PlayerScreen(
                state = playerState,
                onToggle = onToggle,
                onNext = onNext,
                onPrevious = onPrevious,
                onOpenChapters = { navController.navigate(Routes.CHAPTERS) },
                onOpenSentences = { navController.navigate(Routes.SENTENCES) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "${Routes.CHAPTERS}?chapter={chapter}",
            arguments = listOf(navArgument("chapter") {
                type = NavType.IntType
                defaultValue = -1
            }),
        ) { backStackEntry ->
            val initial = backStackEntry.arguments?.getInt("chapter") ?: -1
            ChaptersScreen(
                state = playerState,
                initialSelection = initial,
                onSelect = { onJumpChapter(it) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SENTENCES) {
            SentencesScreen(
                state = playerState,
                onSelect = onSeekSentence,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                state = playerState,
                storageGranted = storageGranted,
                onSpeed = onSpeed,
                onPitch = onPitch,
                onScan = onScan,
                onOpenPicker = { navController.navigate(Routes.PICKER) },
                onGrantShizuku = onGrantShizuku,
                onOpenAllFilesSettings = onOpenAllFilesSettings,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PICKER) {
            PickerScreen(
                files = scanResults,
                scanning = scanning,
                onImport = onImportFile,
                onRescan = onScan,
                onBack = { navController.popBackStack() },
            )
        }
    }

    showMessage?.let { msg ->
        Text(text = msg)
    }
}