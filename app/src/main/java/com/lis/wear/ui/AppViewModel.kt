package com.lis.wear.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lis.wear.book.BookImporter
import com.lis.wear.data.LisRepository
import com.lis.wear.fs.ShizukuFiles
import com.lis.wear.fs.ShizukuShell
import com.lis.wear.model.BookMeta
import com.lis.wear.model.PlayerState
import com.lis.wear.playback.EngineHolder
import com.lis.wear.playback.LisPlaybackService
import com.lis.wear.playback.TtsBookEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** 单条轻提示，UI 用 ConfirmationDialog 展示，2 秒自动关。 */
data class Toast(val text: String, val success: Boolean = true)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LisRepository.get(application)
    val engine: TtsBookEngine = EngineHolder.getOrCreate(application, repository)

    val library: StateFlow<List<BookMeta>> = repository.library
    val playerState: StateFlow<PlayerState> = engine.state

    private val _files = MutableStateFlow<List<ShizukuFiles.RemoteFile>>(emptyList())
    val files: StateFlow<List<ShizukuFiles.RemoteFile>> = _files.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _toast = MutableStateFlow<Toast?>(null)
    val toast: StateFlow<Toast?> = _toast.asStateFlow()

    private val _storageReady = MutableStateFlow(ShizukuFiles.available())
    val storageReady: StateFlow<Boolean> = _storageReady.asStateFlow()

    private val _shizukuRunning = MutableStateFlow(ShizukuShell.isRunning())
    val shizukuRunning: StateFlow<Boolean> = _shizukuRunning.asStateFlow()

    /** Shizuku 授权对话框的回调：授权成功后立刻刷新状态并自动扫描。 */
    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
            refreshShizukuState()
            if (granted) {
                _toast.value = Toast("授权成功")
                scanFiles()
            } else {
                _toast.value = Toast("已拒绝授权", success = false)
            }
        }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshShizukuState()
    }

    init {
        runCatching {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
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
        }
        super.onCleared()
    }

    fun dismissToast() {
        _toast.value = null
    }

    fun refreshShizukuState() {
        _shizukuRunning.value = ShizukuShell.isRunning()
        _storageReady.value = ShizukuFiles.available()
    }

    /** 请求 Shizuku 权限；已就绪时直接扫描。 */
    fun grantShizuku() {
        refreshShizukuState()
        if (!_shizukuRunning.value) {
            _toast.value = Toast("请先在手表上启动 Shizuku", success = false)
            return
        }
        if (_storageReady.value) {
            scanFiles()
            return
        }
        ShizukuShell.requestPermission()
    }

    fun scanFiles() {
        if (_scanning.value) return
        viewModelScope.launch {
            refreshShizukuState()
            if (!_storageReady.value) {
                grantShizuku()
                return@launch
            }
            _scanning.value = true
            val found = withContext(Dispatchers.IO) { ShizukuFiles.list() }
            _scanning.value = false
            _files.value = found
            if (found.isEmpty()) _toast.value = Toast("没找到书籍文件", success = false)
        }
    }

    fun importRemote(file: ShizukuFiles.RemoteFile) {
        viewModelScope.launch {
            _scanning.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val local = ShizukuFiles.copyIntoApp(getApplication(), file)
                        ?: error("读取失败")
                    BookImporter(getApplication()).importFile(local)
                }
            }
            _scanning.value = false
            result.onSuccess { book ->
                repository.saveBook(book)
                _files.value = _files.value.filterNot { it.path == file.path }
                _toast.value = Toast("已导入 ${book.title}")
            }.onFailure {
                _toast.value = Toast(it.message ?: "导入失败", success = false)
            }
        }
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { BookImporter(getApplication()).importUri(uri) }
            }
            result.onSuccess {
                repository.saveBook(it)
                _toast.value = Toast("已导入 ${it.title}")
            }.onFailure {
                _toast.value = Toast(it.message ?: "导入失败", success = false)
            }
        }
    }

    fun openBook(meta: BookMeta, autoPlay: Boolean = true) {
        viewModelScope.launch {
            engine.openBook(meta.id, autoPlay)
            startService()
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
        engine.togglePlayPause()
        startService()
    }

    fun nextChapter() = engine.nextChapter()

    fun previousChapter() = engine.previousChapter()

    fun setSpeed(v: Float) = engine.setSpeed(v)

    fun setPitch(v: Float) = engine.setPitch(v)

    fun jumpChapter(index: Int) {
        engine.jumpChapter(index)
    }

    fun seekToSentence(index: Int) = engine.seekToSentence(index)

    private fun startService() {
        val app = getApplication<Application>()
        val intent = Intent(app, LisPlaybackService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent)
            else app.startService(intent)
        }
    }
}