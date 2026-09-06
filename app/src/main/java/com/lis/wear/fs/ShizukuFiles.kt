package com.lis.wear.fs

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Reads books off the watch's shared storage **without any Android permission**
 * by going through the Shizuku shell.
 *
 * Why not MANAGE_EXTERNAL_STORAGE: granting it via appops only takes effect after
 * the app process restarts, which makes for a terrible watch UX ("授权了但还是读不到").
 * Shizuku's shell already runs with adb-level rights, so we simply `find` and `cat`
 * through it and land the bytes in our own sandbox — instant, no restart, no
 * permission dialog.
 */
object ShizukuFiles {

    private const val TAG = "LisShizukuFiles"

    /** Directories worth scanning, in priority order. */
    private val SCAN_ROOTS = listOf(
        "/sdcard/Download",
        "/sdcard/Documents",
        "/sdcard/Books",
        "/sdcard/books",
        "/sdcard/Bluetooth",
        "/sdcard",
    )

    private val EXTENSIONS = listOf("txt", "epub", "text", "md")

    data class RemoteFile(val path: String, val size: Long) {
        val name: String get() = path.substringAfterLast('/')
        val parent: String get() = path.substringBeforeLast('/', "/")
    }

    fun available(): Boolean = ShizukuShell.isRunning() && ShizukuShell.hasPermission()

    /**
     * List candidate book files. Uses `find` with a depth limit so scanning a
     * large /sdcard stays fast on watch hardware.
     */
    fun list(limit: Int = 300): List<RemoteFile> {
        if (!available()) return emptyList()
        val namePattern = EXTENSIONS.joinToString(" -o ") { "-iname '*.$it'" }
        val out = LinkedHashMap<String, RemoteFile>()

        for (root in SCAN_ROOTS) {
            if (out.size >= limit) break
            val depth = if (root == "/sdcard") 2 else 4
            // -printf isn't available on Android's find; use stat for the size.
            val cmd = "find '$root' -maxdepth $depth -type f \\( $namePattern \\) 2>/dev/null | head -n $limit"
            val result = ShizukuShell.sh(cmd) ?: continue
            if (!result.ok && result.stdout.isBlank()) continue
            for (line in result.stdout.lineSequence()) {
                val path = line.trim()
                if (path.isEmpty() || out.containsKey(path)) continue
                out[path] = RemoteFile(path, size = -1L)
                if (out.size >= limit) break
            }
        }

        // Fill in sizes in one batch call so we can show them in the picker.
        if (out.isNotEmpty()) {
            val paths = out.keys.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
            ShizukuShell.sh("stat -c '%s %n' $paths 2>/dev/null")?.let { stat ->
                for (line in stat.stdout.lineSequence()) {
                    val trimmed = line.trim()
                    val space = trimmed.indexOf(' ')
                    if (space <= 0) continue
                    val size = trimmed.substring(0, space).toLongOrNull() ?: continue
                    val path = trimmed.substring(space + 1)
                    out[path]?.let { out[path] = it.copy(size = size) }
                }
            }
        }

        return out.values.toList()
    }

    /**
     * Copy a shared-storage file into app storage using the shell, so the rest of
     * the app can treat it as a normal local file.
     *
     * Implementation detail: we ask the shell to `cat` the file into a path inside
     * our own files dir. The shell runs as shell/root so it can read the source;
     * our app dir is world-writable enough for that uid on Wear OS. If that fails
     * we fall back to streaming stdout.
     */
    fun copyIntoApp(context: Context, remote: RemoteFile): File? {
        if (!available()) return null
        val target = File(cacheDir(context), remote.name)
        val quotedSrc = "'" + remote.path.replace("'", "'\\''") + "'"
        val quotedDst = "'" + target.absolutePath.replace("'", "'\\''") + "'"

        // Preferred: let the shell write directly, then relax the mode so we can read it.
        val direct = ShizukuShell.sh("cat $quotedSrc > $quotedDst && chmod 666 $quotedDst && echo OK")
        if (direct != null && direct.stdout.contains("OK") && target.length() > 0) {
            return target
        }

        // Fallback: stream the bytes back through stdout.
        return runCatching {
            val result = ShizukuShell.exec("cat", remote.path) ?: return null
            if (result.stdoutBytes.isEmpty()) return null
            target.writeBytes(result.stdoutBytes)
            target.takeIf { it.length() > 0 }
        }.onFailure { Log.w(TAG, "copy failed for ${remote.path}", it) }.getOrNull()
    }

    private fun cacheDir(context: Context): File =
        File(context.filesDir, "imported").apply { mkdirs() }
}