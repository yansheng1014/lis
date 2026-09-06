package com.lis.wear.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ConfirmationDialog
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.lis.wear.R
import com.lis.wear.fs.BookScanner
import com.lis.wear.model.BookMeta
import com.lis.wear.model.PlayerState
import com.lis.wear.model.SleepTimer

object Routes {
    const val LIBRARY = "library"
    const val PLAYER = "player"
    const val CHAPTERS = "chapters"
    const val SETTINGS = "settings"
    const val PICKER = "picker"
}

/**
 * 应用外壳：AppScaffold 提供全局曲面 TimeText，SwipeDismissableNavHost 提供
 * Wear 原生右滑返回。播放页本身是嵌套的分页结构（见 [PlayerPager]）。
 */
@Composable
fun LisNavApp(
    playerState: PlayerState,
    library: List<BookMeta>,
    files: List<BookScanner.LocalFile>,
    busy: Boolean,
    storage: StorageStage,
    toast: Toast?,
    startInPlayer: Boolean,
    startWithScan: Boolean,
    onOpenBook: (BookMeta) -> Unit,
    onImportLocal: (BookScanner.LocalFile) -> Unit,
    onScan: () -> Unit,
    onDeleteBook: (String) -> Unit,
    onPrepareStorage: () -> Unit,
    onDismissToast: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onSleepTimer: (SleepTimer) -> Unit,
    onJumpChapter: (Int) -> Unit,
) {
    val navController = rememberSwipeDismissableNavController()

    LaunchedEffect(startInPlayer, playerState.hasBook) {
        if (startInPlayer && playerState.hasBook) {
            navController.navigate(Routes.PLAYER)
        }
    }

    // 授权重启后自动续上扫描流程。
    LaunchedEffect(startWithScan) {
        if (startWithScan) {
            onScan()
            navController.navigate(Routes.PICKER)
        }
    }

    AppScaffold {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    library = library,
                    playerState = playerState,
                    busy = busy,
                    onOpen = {
                        onOpenBook(it)
                        navController.navigate(Routes.PLAYER)
                    },
                    onScan = {
                        onScan()
                        navController.navigate(Routes.PICKER)
                    },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onDelete = onDeleteBook,
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                )
            }

            composable(Routes.PLAYER) {
                PlayerPager(
                    state = playerState,
                    onToggle = onToggle,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSpeed = onSpeed,
                    onPitch = onPitch,
                    onSleepTimer = onSleepTimer,
                    onOpenChapters = { navController.navigate(Routes.CHAPTERS) },
                )
            }

            composable(Routes.CHAPTERS) {
                ChaptersScreen(
                    state = playerState,
                    onSelect = {
                        onJumpChapter(it)
                        navController.popBackStack()
                    },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    storage = storage,
                    busy = busy,
                    onPrepareStorage = onPrepareStorage,
                    onOpenPicker = {
                        onScan()
                        navController.navigate(Routes.PICKER)
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.PICKER) {
                PickerScreen(
                    files = files,
                    busy = busy,
                    storage = storage,
                    onImport = onImportLocal,
                    onRescan = onScan,
                )
            }
        }
    }

    ConfirmationDialog(
        visible = toast != null,
        onDismissRequest = onDismissToast,
        text = { Text(toast?.text.orEmpty()) },
        content = {
            Icon(
                painter = painterResource(
                    if (toast?.success != false) R.drawable.ic_check else R.drawable.ic_lock
                ),
                contentDescription = null,
                modifier = Modifier.size(ConfirmationDialogDefaults.IconSize),
            )
        },
    )
}