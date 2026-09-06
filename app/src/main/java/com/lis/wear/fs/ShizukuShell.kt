package com.lis.wear.fs

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream

/**
 * 以 shell 身份执行命令（借 Shizuku 的 adb 权限）。
 *
 * Lis 只用它做一件事：给自己写入 MANAGE_EXTERNAL_STORAGE 的 AppOp（一次性）。
 * 之后的扫描与读取全部走原生 `java.io.File`，所以 Shizuku 停止运行也不影响使用。
 *
 * `Shizuku.newProcess` 在公开 API 中是私有的，按 Shizuku 客户端惯例反射调用。
 */
object ShizukuShell {

    private const val TAG = "LisShizuku"
    const val PERMISSION_REQUEST_CODE = 4711

    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        Shizuku.pingBinder() &&
            !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** 用户勾了「不再询问」时为 true。 */
    fun deniedForever(): Boolean = runCatching {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED &&
            !Shizuku.shouldShowRequestPermissionRationale()
    }.getOrDefault(false)

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
            .onFailure { Log.w(TAG, "requestPermission failed", it) }
    }

    data class Result(
        val exitCode: Int,
        val stdout: String,
        val timedOut: Boolean,
    ) {
        val ok: Boolean get() = exitCode == 0 && !timedOut
    }

    /**
     * 执行一条命令并收集 stdout。**只能在 IO 线程调用**（会阻塞到命令结束或超时）。
     *
     * stdout 在独立线程里抽干，调用线程只做带超时的 join —— 远程进程若卡住不输出，
     * 调用方最多等 [timeoutMs]，绝不会永久挂起。
     */
    fun exec(vararg command: String, timeoutMs: Long = 8_000): Result? {
        if (!hasPermission()) return null
        var process: Process? = null
        return runCatching {
            val proc = newProcess(arrayOf(*command)) ?: return null
            process = proc

            val out = ByteArrayOutputStream()
            val pump = Thread {
                runCatching {
                    val buf = ByteArray(16 * 1024)
                    val input = proc.inputStream
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        synchronized(out) { out.write(buf, 0, n) }
                    }
                }
            }.apply { isDaemon = true; start() }

            Thread {
                runCatching {
                    val buf = ByteArray(4 * 1024)
                    val err = proc.errorStream
                    while (err.read(buf) >= 0) { /* discard */ }
                }
            }.apply { isDaemon = true; start() }

            pump.join(timeoutMs)
            val timedOut = pump.isAlive
            if (timedOut) {
                Log.w(TAG, "timeout: ${command.joinToString(" ")}")
                runCatching { proc.destroy() }
                pump.join(300)
            }
            val code = if (timedOut) -1 else runCatching { proc.waitFor() }.getOrDefault(-1)
            val bytes = synchronized(out) { out.toByteArray() }
            Result(code, String(bytes, Charsets.UTF_8), timedOut)
        }.onFailure {
            runCatching { process?.destroy() }
            Log.w(TAG, "exec ${command.joinToString(" ")} failed", it)
        }.getOrNull()
    }

    /** 便捷写法：跑一行 `sh -c`。 */
    fun sh(line: String, timeoutMs: Long = 8_000): Result? =
        exec("sh", "-c", line, timeoutMs = timeoutMs)

    private fun newProcess(command: Array<String>): Process? = runCatching {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).apply { isAccessible = true }
        method.invoke(null, command, null, null) as? Process
    }.onFailure { Log.w(TAG, "newProcess failed", it) }.getOrNull()
}
