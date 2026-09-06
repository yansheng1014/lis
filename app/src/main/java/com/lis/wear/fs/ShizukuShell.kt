package com.lis.wear.fs

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Runs shell commands through Shizuku (adb-level shell). This is what lets Lis
 * read books off /sdcard on a watch that has no file manager, without asking for
 * MANAGE_EXTERNAL_STORAGE (which would need an app restart to take effect).
 *
 * `Shizuku.newProcess` is private in the published API, so it is invoked
 * reflectively — the standard approach for Shizuku clients.
 */
object ShizukuShell {

    private const val TAG = "LisShizuku"
    const val PERMISSION_REQUEST_CODE = 4711

    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        if (Shizuku.isPreV11()) false
        else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
            .onFailure { Log.w(TAG, "requestPermission failed", it) }
    }

    data class Result(val exitCode: Int, val stdout: String, val stdoutBytes: ByteArray) {
        val ok: Boolean get() = exitCode == 0
    }

    /** Execute one command; returns null when Shizuku is unusable. */
    fun exec(vararg command: String): Result? {
        if (!isRunning() || !hasPermission()) return null
        return runCatching {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }

            val process = method.invoke(null, arrayOf(*command), null, null) as Process
            val bytes = process.inputStream.readAllBytesCompat()
            runCatching { process.errorStream.readAllBytesCompat() }
            val code = process.waitFor()
            Result(code, String(bytes, Charsets.UTF_8), bytes)
        }.onFailure { Log.w(TAG, "exec ${command.joinToString(" ")} failed", it) }
            .getOrNull()
    }

    /** Convenience: run a `sh -c` line. */
    fun sh(line: String): Result? = exec("sh", "-c", line)

    private fun InputStream.readAllBytesCompat(): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(32 * 1024)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}