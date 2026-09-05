package com.lis.wear.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lis.wear.ui.theme.LisTheme

class MainActivity : ComponentActivity() {

    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.importUri(it) }
        }

    private val viewModel: AppViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startInPlayer = intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true

        setContent {
            LisTheme {
                val playerState by viewModel.playerState.collectAsStateWithLifecycle()
                val library by viewModel.library.collectAsStateWithLifecycle()
                val scanResults by viewModel.scanResults.collectAsStateWithLifecycle()
                val scanning by viewModel.scanning.collectAsStateWithLifecycle()
                val event by viewModel.events.collectAsStateWithLifecycle()
                val storageGranted by viewModel.storageGranted.collectAsStateWithLifecycle()

                LisApp(
                    playerState = playerState,
                    library = library,
                    scanResults = scanResults,
                    scanning = scanning,
                    storageGranted = storageGranted,
                    event = event,
                    startInPlayer = startInPlayer,
                    onOpenBook = viewModel::openBook,
                    onImportUri = { openDocumentLauncher.launch(arrayOf("text/*", "application/epub+zip")) },
                    onScan = viewModel::scanAndImport,
                    onImportFile = viewModel::importFile,
                    onDeleteBook = viewModel::deleteBook,
                    onGrantShizuku = viewModel::grantStorageViaShizuku,
                    onOpenAllFilesSettings = {
                        com.lis.wear.fs.StoragePermission.openAllFilesSettings(this)
                    },
                    onConsumeEvent = viewModel::consumeEvent,
                    onToggle = viewModel::toggle,
                    onNext = viewModel::nextChapter,
                    onPrevious = viewModel::previousChapter,
                    onSpeed = viewModel::setSpeed,
                    onPitch = viewModel::setPitch,
                    onJumpChapter = viewModel::jumpChapter,
                    onSeekSentence = viewModel::seekToSentence,
                    onRefreshStorage = viewModel::refreshStorageState,
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
        viewModel.refreshStorageState()
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "extra_open_player"
    }
}