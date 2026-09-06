package com.lis.wear.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lis.wear.ui.theme.LisTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startInPlayer = intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true
        // 由授权后自动重启回来的：直接把扫描接着做完，用户不必再点一次。
        val scanOnStart = savedInstanceState == null &&
            intent?.getBooleanExtra(EXTRA_SCAN_FILES, false) == true

        setContent {
            LisTheme {
                val playerState by viewModel.playerState.collectAsStateWithLifecycle()
                val library by viewModel.library.collectAsStateWithLifecycle()
                val files by viewModel.files.collectAsStateWithLifecycle()
                val busy by viewModel.busy.collectAsStateWithLifecycle()
                val toast by viewModel.toast.collectAsStateWithLifecycle()
                val storage by viewModel.storage.collectAsStateWithLifecycle()

                LisNavApp(
                    playerState = playerState,
                    library = library,
                    files = files,
                    busy = busy,
                    storage = storage,
                    toast = toast,
                    startInPlayer = startInPlayer,
                    startWithScan = scanOnStart,
                    onOpenBook = viewModel::openBook,
                    onImportLocal = viewModel::importLocal,
                    onScan = viewModel::scanFiles,
                    onDeleteBook = viewModel::deleteBook,
                    onPrepareStorage = viewModel::prepareStorage,
                    onDismissToast = viewModel::dismissToast,
                    onToggle = viewModel::toggle,
                    onNext = viewModel::nextChapter,
                    onPrevious = viewModel::previousChapter,
                    onSpeed = viewModel::setSpeed,
                    onPitch = viewModel::setPitch,
                    onSleepTimer = viewModel::setSleepTimer,
                    onJumpChapter = viewModel::jumpChapter,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStorage()
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "extra_open_player"
        const val EXTRA_SCAN_FILES = "extra_scan_files"
    }
}