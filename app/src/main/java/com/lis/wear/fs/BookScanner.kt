package com.lis.wear.fs

import android.os.Environment
import com.lis.wear.book.BookImporter
import java.io.File

/**
 * 书籍扫描：三层策略，跟最初能扫到书的那版一样可靠，且更全。
 *
 *  1. 原生 `File` 遍历（MANAGE_EXTERNAL_STORAGE 生效后最快最稳）；
 *  2. Shizuku shell `find` 兜底（原生看不到的书也能找出来）；
 *  3. 无权限时也尽力读（某些目录可能因为系统配置可读）。
 *
 * 顺序执行、无阻塞风险：一切都在 IO 线程执行，且每步都有超时。
 */
object BookScanner {

    /** 优先扫这些目录，最后才兜底扫根目录。 */
    private val PREFERRED_DIRS = listOf(
        "Download", "Documents", "Books", "books", "Bluetooth", "Novel", "novel",
    )

    /** 根目录兜底扫描时跳过的大目录，避免无谓 IO。 */
    private val SKIP_DIRS = setOf(
        "android", "dcim", "pictures", "movies", "music", "ringtones", "notifications",
        "alarms", "podcasts", "samsung", "log", "cache", ".thumbnails", ".trash",
    )

    data class LocalFile(
        val path: String,
        val size: Long,
        val lastModified: Long,
        /** true = 可直接读（原生 File 即可），false = 需要通过 Shizuku shell 拷贝 */
        val direct: Boolean = true,
    ) {
        val name: String get() = path.substringAfterLast('/')
        val parent: String get() = path.substringBeforeLast('/', "/")
    }

    /** 必须在 IO 线程调用。 */
    fun scan(limit: Int = 500): List<LocalFile> {
        val found = LinkedHashMap<String, LocalFile>()

        // ---- 1. 原生遍历 ---------------------------------------------------
        for (root in nativeRoots()) {
            if (found.size >= limit) break
            for (name in PREFERRED_DIRS) {
                if (found.size >= limit) break
                walk(File(root, name), depth = 4, out = found, limit = limit, skipNoise = false)
            }
        }
        for (root in nativeRoots()) {
            if (found.size >= limit) break
            walk(root, depth = 4, out = found, limit = limit, skipNoise = true)
        }

        // ---- 2. Shizuku shell 兜底（在线且有权限时） ------------------------
        if (found.size < limit && ShizukuShell.hasPermission()) {
            scanViaShell(found, limit)
        }

        return found.values.sortedWith(
            compareByDescending<LocalFile> { it.lastModified }.thenBy { it.name }
        )
    }

    // region native walk

    /** 原生可读的根目录集合（去重）。 */
    private fun nativeRoots(): List<File> = buildList {
        runCatching { Environment.getExternalStorageDirectory() }.getOrNull()?.let { add(it) }
        add(File("/sdcard"))
        add(File("/storage/emulated/0"))
    }.distinctBy { it.absolutePath }.filter { runCatching { it.exists() }.getOrDefault(false) }

    private fun walk(
        dir: File,
        depth: Int,
        out: MutableMap<String, LocalFile>,
        limit: Int,
        skipNoise: Boolean,
    ) {
        if (depth < 0 || out.size >= limit) return
        if (!runCatching { dir.isDirectory }.getOrDefault(false)) return
        val entries = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (entry in entries) {
            if (out.size >= limit) return
            val name = runCatching { entry.name }.getOrNull() ?: continue
            if (entry.isDirectory) {
                if (name.startsWith(".")) continue
                if (skipNoise && name.lowercase() in SKIP_DIRS) continue
                walk(entry, depth - 1, out, limit, skipNoise)
            } else if (BookImporter.isSupported(name)) {
                val path = runCatching { entry.absolutePath }.getOrNull() ?: continue
                if (out.containsKey(path)) continue
                val size = runCatching { entry.length() }.getOrDefault(0L)
                if (size <= 0L) continue
                out[path] = LocalFile(
                    path,
                    size,
                    runCatching { entry.lastModified() }.getOrDefault(0L),
                    direct = true,
                )
            }
        }
    }

    // endregion

    // region shell fallback

    /** 用 shell `find` 全盘找书（Shizuku 在线时）；原生看不到的也能找出来。 */
    private fun scanViaShell(out: MutableMap<String, LocalFile>, limit: Int) {
        val namePattern = BookImporter.SUPPORTED_EXT.joinToString(" -o ") { "-iname \"*$it\"" }
        val cmd = buildString {
            append("find /sdcard /storage/emulated/0 -type f \\( $namePattern \\) ")
            append("2>/dev/null | head -n $limit")
        }
        val result = ShizukuShell.sh(cmd, timeoutMs = 12_000) ?: return
        for (line in result.stdout.lineSequence()) {
            if (out.size >= limit) return
            val path = line.trim()
            if (path.isEmpty() || out.containsKey(path)) continue
            out[path] = LocalFile(path, size = -1L, lastModified = 0L, direct = false)
        }
        fillSizes(out)
    }

    /** 补齐 shell 文件的真实大小，便于列表展示与排序。 */
    private fun fillSizes(files: MutableMap<String, LocalFile>) {
        val unknown = files.values.filter { it.size < 0 && !it.direct }
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

    // endregion
}