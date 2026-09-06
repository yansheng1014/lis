package com.lis.wear.fs

import android.content.Context
import android.os.Environment
import android.util.Log
import com.lis.wear.book.BookImporter
import java.io.File

/**
 * Finds book files on the watch.
 *
 * Two paths, tried in order:
 *  1. Plain `java.io.File` walk — works for anything the app can already read
 *     (its own dirs, and /sdcard on watches that still allow legacy access).
 *  2. Shizuku shell (`find` / `cat`) — needed on API 33+ where scoped storage
 *     blocks /sdcard. Nothing is installed or restarted, so it takes effect
 *     immediately after the user approves Shizuku.
 */
object ShizukuFiles {

    private const val TAG = "LisFiles"

    private val SCAN_ROOTS = listOf(
        "/sdcard/Download",
        "/sdcard/Documents",
        "/sdcard/Books",
        "/sdcard/books",
        "/sdcard/Bluetooth",
        "/storage/emulated/0/Download",
        "/storage/emulated/0/Documents",
        "/sdcard",
    )

    private val EXTENSIONS = listOf("txt", "epub", "text", "md")

    data class RemoteFile(
        val path: String,
        val size: Long,
        /** true when the file is readable directly, no shell needed */
        val direct: Boolean,
    ) {
        val name: String get() = path.substringAfterLast('/')
        val parent: String get() = path.substringBeforeLast('/', "/")
    }

    fun shizukuReady(): Boolean = ShizukuShell.hasPermission()

    /**
     * Scan for books. Always tries a direct filesystem walk first so the app is
     * useful even without Shizuku, then augments with a shell scan when allowed.
     */
    fun scan(limit: Int = 200): List<RemoteFile> {
        val found = LinkedHashMap<String, RemoteFile>()

        // --- 1. direct walk -------------------------------------------------
        for (root in directRoots()) {
            if (found.size >= limit) break
            walkDirect(root, depth = 3, out = found, limit = limit)
        }

        // --- 2. shell walk --------------------------------------------------
        if (found.size < limit && shizukuReady()) {
            val namePattern = EXTENSIONS.joinToString(" -o ") { "-iname '*.$it'" }
            for (root in SCAN_ROOTS) {
                if (found.size >= limit) break
                val depth = if (root == "/sdcard" || root.endsWith("emulated/0")) 2 else 4
                val cmd =
                    "find '$root' -maxdepth $depth -type f \\( $namePattern \\) 2>/dev/null | head -n $limit"
                val result = ShizukuShell.sh(cmd, timeoutMs = 12_000) ?: continue
                for (line in result.stdout.lineSequence()) {
                    val path = line.trim()
                    if (path.isEmpty() || found.containsKey(path)) continue
                    found[path] = RemoteFile(path, size = -1L, direct = false)
                    if (found.size >= limit) break
                }
            }
            fillSizes(found)
        }

        return found.values.sortedBy { it.name }
    }

    /** Roots we can read without any special permission. */
    private fun directRoots(): List<File> = buildList {
        runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
            ?.let { root ->
                add(File(root, "Download"))
                add(File(root, "Documents"))
                add(File(root, "Books"))
                add(root)
            }
    }.filter { it.exists() && it.canRead() }
        .distinctBy { it.absolutePath }

    private fun walkDirect(dir: File, depth: Int, out: MutableMap<String, RemoteFile>, limit: Int) {
        if (depth < 0 || out.size >= limit) return
        val entries = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (entry in entries) {
            if (out.size >= limit) return
            if (entry.isDirectory) {
                walkDirect(entry, depth - 1, out, limit)
            } else if (BookImporter.isSupported(entry.name) && entry.canRead()) {
                out[entry.absolutePath] =
                    RemoteFile(entry.absolutePath, entry.length(), direct = true)
            }
        }
    }

    private fun fillSizes(files: MutableMap<String, RemoteFile>) {
        val unknown = files.values.filter { it.size < 0 }
        if (unknown.isEmpty()) return
        val quoted = unknown.joinToString(" ") { "'" + it.path.replace("'", "'\\''") + "'" }
        val stat = ShizukuShell.sh("stat -c '%s %n' $quoted 2>/dev/null", timeoutMs = 8_000)
            ?: return
        for (line in stat.stdout.lineSequence()) {
            val trimmed = line.trim()
            val space = trimmed.indexOf(' ')
            if (space <= 0) continue
            val size = trimmed.substring(0, space).toLongOrNull() ?: continue
            val path = trimmed.substring(space + 1)
            files[path]?.let { files[path] = it.copy(size = size) }
        }
    }

    /**
     * Make the file available as a local `File`. Direct-readable files are used
     * as-is; shell-only files are copied into app storage.
     */
    fun materialise(context: Context, remote: RemoteFile): File? {
        if (remote.direct) {
            val f = File(remote.path)
            if (f.canRead()) return f
        }
        if (!shizukuReady()) return null

        val target = File(cacheDir(context), remote.name)
        val quotedSrc = "'" + remote.path.replace("'", "'\\''") + "'"
        val quotedDst = "'" + target.absolutePath.replace("'", "'\\''") + "'"

        val direct = ShizukuShell.sh(
            "cat $quotedSrc > $quotedDst && chmod 666 $quotedDst && echo LIS_OK",
            timeoutMs = 60_000,
        )
        if (direct != null && direct.stdout.contains("LIS_OK") && target.length() > 0) {
            return target
        }

        return runCatching {
            val result = ShizukuShell.exec("cat", remote.path, timeoutMs = 60_000) ?: return null
            if (result.stdoutBytes.isEmpty()) return null
            target.writeBytes(result.stdoutBytes)
            target.takeIf { it.length() > 0 }
        }.onFailure { Log.w(TAG, "copy failed for ${remote.path}", it) }.getOrNull()
    }

    private fun cacheDir(context: Context): File =
        File(context.filesDir, "imported").apply { mkdirs() }
}