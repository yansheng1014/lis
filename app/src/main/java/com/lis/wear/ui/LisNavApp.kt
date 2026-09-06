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
import com.lis.wear.fs.ShizukuFiles
import com.lis.wear.model.BookMeta
import com.lis.wear.model.PlayerState

object Routes {
    const val LIBRARY = "library"
    const val PLAYER = "player"
    const val CHAPTERS = "chapters"
    const val SENTENCES = "sentences"
    const val SETTINGS = "settings"
    const val PICKER = "picker"
}

/**
 * 应用外壳：AppScaffold 提供全局 TimeText（曲面时间），
 * SwipeDismissableNavHost 提供 Wear 原生右滑返回动画。
 */
@Composable
fun LisNavApp(
    playerState: PlayerState,
    library: List<BookMeta>,
    files: List<ShizukuFiles.RemoteFile>,
    scanning: Boolean,
    storageReady: Boolean,
    shizukuRunning: Boolean,
    toast: Toast?,
    startInPlayer: Boolean,
    onOpenBook: (BookMeta) -> Unit,
    onImportUri: () -> Unit,
    onImportRemote: (ShizukuFiles.RemoteFile) -> Unit,
    onScan: () -> Unit,
    onDeleteBook: (String) -> Unit,
    onGrantShizuku: () -> Unit,
    onDismissToast: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onJumpChapter: (Int) -> Unit,
    onSeekSentence: (Int) -> Unit,
) {
    val navController = rememberSwipeDismissableNavController()

    LaunchedEffect(startInPlayer, playerState.hasBook) {
        if (startInPlayer && playerState.hasBook) {
            navController.navigate(Routes.PLAYER)
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
                    onOpen = {
                        onOpenBook(it)
                        navController.navigate(Routes.PLAYER)
                    },
                    onImportUri = onImportUri,
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

            composable(Routes.CHAPTERS) {
                ChaptersScreen(
                    state = playerState,
                    onSelect = {
                        onJumpChapter(it)
                        navController.popBackStack()
                    },
                )
            }

            composable(Routes.SENTENCES) {
                SentencesScreen(
                    state = playerState,
                    onSelect = onSeekSentence,
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    state = playerState,
                    storageReady = storageReady,
                    shizukuRunning = shizukuRunning,
                    onSpeed = onSpeed,
                    onPitch = onPitch,
                    onOpenPicker = { navController.navigate(Routes.PICKER) },
                    onGrantShizuku = onGrantShizuku,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.PICKER) {
                PickerScreen(
                    files = files,
                    scanning = scanning,
                    storageReady = storageReady,
                    onImport = onImportRemote,
                    onRescan = onScan,
                    onGrantShizuku = onGrantShizuku,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }

    // 全屏轻提示：Wear 原生 ConfirmationDialog，自带图标动画和自动消失
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
                modifier = Modifier.size(ConfirmationDialogDefaults.SmallIconSize),
            )
        },
    )
}