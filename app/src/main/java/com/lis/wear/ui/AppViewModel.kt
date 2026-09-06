package com.lis.wear.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lis.wear.book.BookImporter
import com.lis.wear.data.LisRepository
import com.lis.wear.fs.ShizukuFiles
import com.lis.wear.fs.ShizukuShell
import com.lis.wear.model.BookMeta
import com.lis.wear.model.PlayerState
import com.lis.wear.model.SleepTimer
import com.lis.wear.playback.EngineHolder
import com.lis.wear.playback.LisPlaybackService
import com.lis.wear.playback.TtsBookEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** Toast-like message; rendered by a Wear ConfirmationDialog. */
data class Toast(val text: String, val success: Boolean = true)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LisRepository.get(application)
    val engine: TtsBookEngine = EngineHolder.getOrCreate(application, repository)

    val library: StateFlow<List<BookMeta>> = repository.library
    val playerState: StateFlow<PlayerState> = engine.state

    private val _files = MutableStateFlow<List<ShizukuFiles.RemoteFile>>(emptyList())
    val files: StateFlow<List<ShizukuFiles.RemoteFile>> = _files.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _toast = MutableStateFlow<Toast?>(null)
    val toast: StateFlow<Toast?> = _toast.asStateFlow()

    private val _shizukuReady = MutableStateFlow(ShizukuShell.hasPermission())
    val shizukuReady: StateFlow<Boolean> = _shizukuReady.asStateFlow()

    private val _shizukuRunning = MutableStateFlow(ShizukuShell.isRunning())
    val shizukuRunning: StateFlow<Boolean> = _shizukuRunning.asStateFlow()

    /** Set when a scan should immediately follow a successful grant. */
    private var scanAfterGrant = false

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            refreshShizukuState()
            if (granted) {
                _toast.value = Toast("Shizuku 已授权")
                if (scanAfterGrant) {
                    scanAfterGrant = false
                    scanFiles()
                }
            } else {
                scanAfterGrant = false
                _toast.value = Toast("已拒绝授权", success = false)
            }
        }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshShizukuState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        refreshShizukuState()
    }

    init {
        runCatching {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        }
        viewModelScope.launch {
            engine.ensureTts()
            engine.resumeLast(autoPlay = false)
        }
    }

    override fun onCleared() {
        runCatching {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        }
        super.onCleared()
    }

    fun dismissToast() {
        _toast.value = null
    }

    fun refreshShizukuState() {
        _shizukuRunning.value = ShizukuShell.isRunning()
        _shizukuReady.value = ShizukuShell.hasPermission()
    }

    /**
     * Single entry point used by every "scan" button. It handles the whole flow:
     * grant Shizuku if needed, then scan, and always reports the outcome.
     */
    fun scanFiles() {
        if (_busy.value) return
        refreshShizukuState()

        if (!_shizukuReady.value) {
            if (!_shizukuRunning.value) {
                // Still try a permission-free walk — some watches allow legacy reads.
                viewModelScope.launch { runScan(afterGrantHint = true) }
                return
            }
            scanAfterGrant = true
            ShizukuShell.requestPermission()
            return
        }
        viewModelScope.launch { runScan(afterGrantHint = false) }
    }

    private suspend fun runScan(afterGrantHint: Boolean) {
        _busy.value = true
        val found = withContext(Dispatchers.IO) {
            runCatching { ShizukuFiles.scan() }.getOrElse {
                Log.w(TAG, "scan failed", it)
                emptyList()
            }
        }
        _busy.value = false
        _files.value = found
        if (found.isEmpty()) {
            _toast.value = Toast(
                if (afterGrantHint) "请在手表启动 Shizuku 后重试" else "没找到 txt / epub",
                success = false,
            )
        }
    }

    fun grantShizuku() {
        refreshShizukuState()
        when {
            _shizukuReady.value -> scanFiles()
            !_shizukuRunning.value ->
                _toast.value = Toast("请先在手表上启动 Shizuku", success = false)
            else -> {
                scanAfterGrant = true
                ShizukuShell.requestPermission()
            }
        }
    }

    fun importRemote(file: ShizukuFiles.RemoteFile) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val local = ShizukuFiles.materialise(getApplication(), file)
                        ?: error("无法读取该文件")
                    BookImporter(getApplication()).importFile(local)
                }
            }
            _busy.value = false
            result.onSuccess { book ->
                repository.saveBook(book)
                _files.value = _files.value.filterNot { it.path == file.path }
                _toast.value = Toast("已导入 ${book.title}")
            }.onFailure {
                Log.w(TAG, "import failed", it)
                _toast.value = Toast(it.message ?: "导入失败", success = false)
            }
        }
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching { BookImporter(getApplication()).importUri(uri) }
            }
            _busy.value = false
            result.onSuccess {
                repository.saveBook(it)
                _toast.value = Toast("已导入 ${it.title}")
            }.onFailure {
                _toast.value = Toast(it.message ?: "导入失败", success = false)
            }
        }
    }

    fun openBook(meta: BookMeta) {
        viewModelScope.launch {
            LisPlaybackService.ensureRunning(getApplication())
            engine.openBook(meta.id, autoPlay = true)
        }
    }

    fun deleteBook(id: String) {
        viewModelScope.launch {
            if (playerState.value.bookId == id) engine.stop()
            repository.deleteBook(id)
            _toast.value = Toast("已删除")
        }
    }

    fun toggle() {
        LisPlaybackService.ensureRunning(getApplication())
        engine.togglePlayPause()
    }

    fun nextChapter() {
        engine.nextChapter()
    }

    fun previousChapter() {
        engine.previousChapter()
    }

    fun setSpeed(v: Float) = engine.setSpeed(v)

    fun setPitch(v: Float) = engine.setPitch(v)

    fun setSleepTimer(timer: SleepTimer) {
        engine.setSleepTimer(timer)
        _toast.value = Toast(
            if (timer == SleepTimer.OFF) "已关闭定时" else "定时：${timer.label}"
        )
    }

    fun jumpChapter(index: Int) {
        engine.jumpChapter(index)
    }

    fun seekToSentence(index: Int) = engine.seekToSentence(index)

    companion object {
        private const val TAG = "LisVm"
    }
}