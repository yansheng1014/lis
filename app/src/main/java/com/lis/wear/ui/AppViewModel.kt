package com.lis.wear.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lis.wear.book.BookImporter
import com.lis.wear.data.LisRepository
import com.lis.wear.fs.BookScanner
import com.lis.wear.fs.StoragePermission
import com.lis.wear.model.Book
import com.lis.wear.model.BookMeta
import com.lis.wear.model.PlayerState
import com.lis.wear.playback.EngineHolder
import com.lis.wear.playback.LisPlaybackService
import com.lis.wear.playback.TtsBookEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    object ScanDone : UiEvent
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LisRepository.get(application)
    val engine: TtsBookEngine = EngineHolder.getOrCreate(application, repository)

    val library: StateFlow<List<BookMeta>> = repository.library
    val playerState: StateFlow<PlayerState> = engine.state

    private val _scanResults = MutableStateFlow<List<File>>(emptyList())
    val scanResults: StateFlow<List<File>> = _scanResults.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _events = MutableStateFlow<UiEvent?>(null)
    val events: StateFlow<UiEvent?> = _events.asStateFlow()

    private val _storageGranted = MutableStateFlow(StoragePermission.canScanStorage(application))
    val storageGranted: StateFlow<Boolean> = _storageGranted.asStateFlow()

    init {
        viewModelScope.launch {
            engine.ensureTts()
            // Auto-restore the last book so the player page has context on relaunch.
            engine.resumeLast(autoPlay = false)
        }
    }

    fun consumeEvent() {
        _events.value = null
    }

    fun refreshStorageState() {
        _storageGranted.value = StoragePermission.canScanStorage(getApplication())
    }

    fun openBook(meta: BookMeta, autoPlay: Boolean = true) {
        viewModelScope.launch {
            engine.openBook(meta.id, autoPlay)
            startService()
        }
    }

    fun importUri(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                val importer = BookImporter(getApplication())
                val book = importer.importUri(uri)
                repository.saveBook(book)
            }.onSuccess {
                _events.value = UiEvent.Message("已导入「${it.title}」")
            }.onFailure {
                _events.value = UiEvent.Message("导入失败：${it.message}")
            }
        }
    }

    fun scanAndImport() {
        if (_scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            val result = BookScanner.scan()
            _scanning.value = false
            if (result.error != null) {
                _events.value = UiEvent.Message("扫描失败：${result.error}")
                return@launch
            }
            _scanResults.value = result.files
            _events.value = UiEvent.ScanDone
        }
    }

    fun importFile(file: File) {
        viewModelScope.launch {
            runCatching {
                val book = BookImporter(getApplication()).importFile(file)
                repository.saveBook(book)
            }.onSuccess {
                _events.value = UiEvent.Message("已导入「${it.title}」")
                _scanResults.value = _scanResults.value.filterNot { it.absolutePath == file.absolutePath }
            }.onFailure {
                _events.value = UiEvent.Message("导入失败：${it.message}")
            }
        }
    }

    fun deleteBook(id: String) {
        viewModelScope.launch {
            engine.stop()
            repository.deleteBook(id)
        }
    }

    fun grantStorageViaShizuku() {
        viewModelScope.launch {
            if (!StoragePermission.isShizukuAvailable()) {
                _events.value = UiEvent.Message("未检测到 Shizuku，请先在手机上启动 Shizuku")
                return@launch
            }
            StoragePermission.requestShizukuPermission()
            val ok = StoragePermission.grantAllFilesViaShizuku(getApplication())
            refreshStorageState()
            _events.value = UiEvent.Message(if (ok) "存储授权成功" else "授权失败，请手动在系统设置里开启“所有文件访问”")
        }
    }

    fun toggle() = engine.togglePlayPause()

    fun nextChapter() = engine.nextChapter()

    fun previousChapter() = engine.previousChapter()

    fun setSpeed(v: Float) = engine.setSpeed(v)

    fun setPitch(v: Float) = engine.setPitch(v)

    fun jumpChapter(index: Int) = engine.jumpChapter(index)

    fun seekToSentence(index: Int) = engine.seekToSentence(index)

    private fun startService() {
        val app = getApplication<Application>()
        val intent = Intent(app, LisPlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent)
        else app.startService(intent)
    }
}