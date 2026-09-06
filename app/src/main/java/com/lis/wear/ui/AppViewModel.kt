package com.lis.wear.ui

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lis.wear.RestartActivity
import com.lis.wear.book.BookImporter
import com.lis.wear.data.LisRepository
import com.lis.wear.fs.BookScanner
import com.lis.wear.fs.ShizukuShell
import com.lis.wear.fs.StorageAccess
import com.lis.wear.model.BookMeta
import com.lis.wear.model.PlayerState
import com.lis.wear.model.SleepTimer
import com.lis.wear.playback.EngineHolder
import com.lis.wear.playback.LisPlaybackService
import com.lis.wear.playback.TtsBookEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.File

/** Toast-like message; rendered by a Wear ConfirmationDialog. */
data class Toast(val text: String, val success: Boolean = true)

/** 共享存储可用性。UI 只看这一个枚举，不再各自判断 Shizuku。 */
enum class StorageStage {
    /** 能直接读 /sdcard，Shizuku 已经无关紧要。 */
    READY,

    /** 权限已写入，但当前进程的挂载还是旧的，重启一次即可。 */
    NEED_RESTART,

    /** Shizuku 在跑，还没授权给 Lis。 */
    NEED_GRANT,

    /** Shizuku 没启动。 */
    NEED_SHIZUKU,
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LisRepository.get(application)
    val engine: TtsBookEngine = EngineHolder.getOrCreate(application, repository)

    val library: StateFlow<List<BookMeta>> = repository.library
    val playerState: StateFlow<PlayerState> = engine.state

    private val _files = MutableStateFlow<List<BookScanner.LocalFile>>(emptyList())
    val files: StateFlow<List<BookScanner.LocalFile>> = _files.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _toast = MutableStateFlow<Toast?>(null)
    val toast: StateFlow<Toast?> = _toast.asStateFlow()

    private val _storage = MutableStateFlow(StorageStage.NEED_SHIZUKU)
    val storage: StateFlow<StorageStage> = _storage.asStateFlow()

    /** 由 Shizuku 回调置位，用来立刻结束等待，不必等轮询超时。 */
    @Volatile
    private var permissionDenied = false

    /** 一个进程只自动重启一次，避免异常 ROM 上反复重启。 */
    private var autoRestartUsed = false

    private var workJob: Job? = null

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            permissionDenied = grantResult != PackageManager.PERMISSION_GRANTED
            refreshStorage()
        }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refreshStorage() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { refreshStorage() }

    init {
        runCatching {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        }
        refreshStorage()
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

    // region storage

    /** 探测放在 IO 线程，主线程只拿结果，onResume 调用也不会卡帧。 */
    fun refreshStorage() {
        viewModelScope.launch {
            _storage.value = withContext(Dispatchers.IO) { probeStage() }
        }
    }

    private fun probeStage(): StorageStage = when {
        StorageAccess.canReadShared() -> StorageStage.READY
        StorageAccess.hasAllFilesOp() -> StorageStage.NEED_RESTART
        ShizukuShell.isRunning() -> StorageStage.NEED_GRANT
        else -> StorageStage.NEED_SHIZUKU
    }

    /**
     * 唯一的扫描入口：需要就先把权限拿到，再扫盘。全流程带超时，
     * `busy` 不可能卡死。
     */
    fun scanFiles() {
        if (workJob?.isActive == true) return
        workJob = viewModelScope.launch {
            _busy.value = true
            try {
                if (ensureStorage()) runScan()
            } finally {
                _busy.value = false
            }
        }
    }

    /** 设置页的「授权 / 重启生效」按钮。 */
    fun prepareStorage() {
        if (workJob?.isActive == true) return
        workJob = viewModelScope.launch {
            _busy.value = true
            try {
                if (ensureStorage()) {
                    _toast.value = Toast("存储已就绪")
                    runScan()
                }
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * 让 /sdcard 可读。返回 true 表示现在就能读。
     *
     * 步骤：Shizuku 授权 → 用 shell 写 MANAGE_EXTERNAL_STORAGE 的 AppOp →
     * 真实探测；探测不过说明挂载没刷新，重启一次进程即可，之后永久生效。
     */
    private suspend fun ensureStorage(): Boolean {
        var stage = withContext(Dispatchers.IO) { probeStage() }
        _storage.value = stage
        if (stage == StorageStage.READY) return true

        if (stage == StorageStage.NEED_RESTART) {
            requestRestart()
            return false
        }

        if (!ShizukuShell.isRunning()) {
            _toast.value = Toast("请先在手表上启动 Shizuku", success = false)
            return false
        }

        if (!ShizukuShell.hasPermission()) {
            if (ShizukuShell.deniedForever()) {
                _toast.value = Toast("请在 Shizuku 里手动放行 Lis", success = false)
                return false
            }
            permissionDenied = false
            ShizukuShell.requestPermission()
            if (!awaitShizukuPermission()) {
                _storage.value = withContext(Dispatchers.IO) { probeStage() }
                _toast.value = Toast("未获得 Shizuku 授权", success = false)
                return false
            }
        }

        val granted = withContext(Dispatchers.IO) {
            StorageAccess.grantAllFiles(getApplication())
        }
        if (!granted) {
            _toast.value = Toast("授权失败，请重开 Shizuku 再试", success = false)
            return false
        }

        // 有的机型热重挂载即时生效，给它几百毫秒；不生效就走重启。
        val readable = withContext(Dispatchers.IO) {
            repeat(6) {
                if (StorageAccess.canReadShared()) return@withContext true
                Thread.sleep(250)
            }
            StorageAccess.canReadShared()
        }
        stage = if (readable) StorageStage.READY else StorageStage.NEED_RESTART
        _storage.value = stage
        if (readable) return true

        requestRestart()
        return false
    }

    /**
     * 轮询等待 Shizuku 授权结果。回调偶尔丢失（授权页返回时进程被冻结），
     * 轮询兜底后就不会再出现「明明点了允许，还要我再点一次」。
     */
    private suspend fun awaitShizukuPermission(timeoutMs: Long = 60_000): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (ShizukuShell.hasPermission()) return true
            if (permissionDenied) return false
            delay(250)
        }
        return ShizukuShell.hasPermission()
    }

    /**
     * AppOp 写好了但当前进程的挂载视图还是旧的 —— 只能换个新进程。
     * 播放中不打断；跨进程冷却保证不会出现重启死循环。
     */
    private suspend fun requestRestart() {
        if (engine.isPlaying) {
            _toast.value = Toast("已授权，播放结束后重开 Lis 生效", success = false)
            return
        }
        val app = getApplication<Application>()
        if (autoRestartUsed || !StorageAccess.canAutoRestart(app)) {
            _toast.value = Toast("已授权，重开 Lis 即可生效", success = false)
            return
        }
        autoRestartUsed = true
        StorageAccess.markRestart(app)
        _toast.value = Toast("已授权，正在重启应用")
        // 给对话框一点显示时间，随后进程被替换。
        delay(700)
        runCatching { RestartActivity.trigger(app, scanAfterRestart = true) }
            .onFailure {
                Log.w(TAG, "restart failed", it)
                _toast.value = Toast("已授权，重开 Lis 即可生效", success = false)
            }
    }

    private suspend fun runScan() {
        val found = withTimeoutOrNull(20_000) {
            withContext(Dispatchers.IO) {
                runCatching { BookScanner.scan() }.getOrElse {
                    Log.w(TAG, "scan failed", it)
                    emptyList()
                }
            }
        } ?: emptyList()

        _files.value = found
        if (found.isEmpty()) {
            _toast.value = Toast("手表中未找到 txt / epub 书籍", success = false)
        }
    }

    // endregion

    fun importLocal(file: BookScanner.LocalFile) {
        if (workJob?.isActive == true) return
        workJob = viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching { BookImporter(getApplication()).importFile(File(file.path)) }
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
        if (workJob?.isActive == true) return
        workJob = viewModelScope.launch {
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