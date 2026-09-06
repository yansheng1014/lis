package com.lis.wear.fs

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * 共享存储访问：借 Shizuku 一次性拿到「所有文件访问」，之后全程用原生 [File] 读写。
 *
 * 手表没有文件管理器、也没有「所有文件访问」的设置项，所以用 shell 身份写一次
 * AppOp。写完之后：
 *  - Shizuku 可以随时停掉，Lis 依旧能扫盘、能读书；
 *  - 只有卸载重装才需要再授权一次。
 *
 * AppOp 生效分两种情况：系统热刷新（立刻可读）或需要进程重建（挂载视图在进程
 * fork 时定型）。所以这里不猜，用 [canReadShared] 做真实探测。
 */
object StorageAccess {

    private const val TAG = "LisStorage"

    /** AppOp 是否已允许（不代表当前进程的挂载视图已经刷新）。 */
    fun hasAllFilesOp(): Boolean =
        runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)

    /**
     * 真实可读性探测。AppOp 已允许但进程挂载仍是旧的时候返回 false —— 这正是
     * 「授权了却一直转圈」的根源，所以权限判断一律以这个为准。
     */
    fun canReadShared(): Boolean {
        val root = sharedRoot() ?: return false
        val probes = listOf(File(root, "Download"), File(root, "Documents"), root)
        return probes.any { dir ->
            runCatching { dir.isDirectory && dir.listFiles() != null }.getOrDefault(false)
        }
    }

    fun sharedRoot(): File? =
        runCatching { Environment.getExternalStorageDirectory() }.getOrNull()

    /**
     * 给自己写 MANAGE_EXTERNAL_STORAGE 的 AppOp。uid 维度和包维度都写一遍，
     * 不同 ROM 的读取顺序不一样；`appops` 与 `cmd appops` 也都试一次。
     *
     * 结果以 AppOp 的真实状态为准（[hasAllFilesOp] 读的是实时值），不看 shell
     * 退出码 —— 命令跑通但被系统拒绝时退出码也可能是 0。
     *
     * 必须在 IO 线程调用。
     */
    fun grantAllFiles(context: Context): Boolean {
        if (hasAllFilesOp()) return true

        val pkg = context.packageName
        val op = "MANAGE_EXTERNAL_STORAGE"
        val line = buildString {
            append("appops set --uid $pkg $op allow 2>&1; ")
            append("cmd appops set --uid $pkg $op allow 2>&1; ")
            append("appops set $pkg $op allow 2>&1; ")
            append("cmd appops set $pkg $op allow 2>&1")
        }
        val result = ShizukuShell.sh(line, timeoutMs = 8_000)
        Log.i(TAG, "grantAllFiles shell=${result?.stdout?.trim()}")

        // AppOp 立刻可读，但系统落库有极短延迟，轮询一小会儿。
        repeat(8) {
            if (hasAllFilesOp()) return true
            Thread.sleep(150)
        }
        return hasAllFilesOp()
    }

    /**
     * 自动重启的冷却判断。防止「AppOp 写进去了但系统就是不放行」的机型陷入
     * 重启 → 探测失败 → 再重启的死循环：一段时间内只允许重启一次。
     */
    fun canAutoRestart(context: Context): Boolean {
        val last = prefs(context).getLong(KEY_LAST_RESTART, 0L)
        val now = System.currentTimeMillis()
        // 时钟回跳也算冷却结束，避免永久锁死。
        return now < last || now - last > RESTART_COOLDOWN_MS
    }

    fun markRestart(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_RESTART, System.currentTimeMillis()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("lis_storage", Context.MODE_PRIVATE)

    private const val KEY_LAST_RESTART = "last_restart_at"
    private const val RESTART_COOLDOWN_MS = 5 * 60 * 1000L
}