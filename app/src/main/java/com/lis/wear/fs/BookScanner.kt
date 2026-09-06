package com.lis.wear.fs

import com.lis.wear.book.BookImporter
import java.io.File

/**
 * 书籍扫描：纯 `java.io.File` 遍历，不依赖 Shizuku 在线。
 *
 * 权限由 [StorageAccess] 一次性搞定，之后这里就是普通的文件遍历，因此扫描很快、
 * 不会卡住，也不受 Shizuku 是否运行影响。
 */
object BookScanner {

    /** 优先扫这几个目录，最后才兜底扫根目录（限制层数避免翻遍整盘）。 */
    private val PREFERRED_DIRS = listOf(
        "Download", "Documents", "Books", "books", "Bluetooth", "Novel", "novel",
    )

    /** 单个目录最多下探的层数。 */
    private const val DEPTH = 3

    /** 根目录兜底扫描时跳过的大目录，避免无谓 IO。 */
    private val SKIP_DIRS = setOf(
        "android", "dcim", "pictures", "movies", "music", "ringtones", "notifications",
        "alarms", "podcasts", "samsung", "log", "cache", ".thumbnails", ".trash",
    )

    data class LocalFile(val path: String, val size: Long, val lastModified: Long) {
        val name: String get() = path.substringAfterLast('/')
        val parent: String get() = path.substringBeforeLast('/', "/")
    }

    /** 必须在 IO 线程调用。 */
    fun scan(limit: Int = 300): List<LocalFile> {
        val root = StorageAccess.sharedRoot() ?: return emptyList()
        val found = LinkedHashMap<String, LocalFile>()

        for (name in PREFERRED_DIRS) {
            if (found.size >= limit) break
            walk(File(root, name), DEPTH, found, limit, skipNoise = false)
        }
        if (found.size < limit) {
            walk(root, 2, found, limit, skipNoise = true)
        }

        return found.values.sortedWith(
            compareByDescending<LocalFile> { it.lastModified }.thenBy { it.name }
        )
    }

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
                out[path] = LocalFile(path, size, runCatching { entry.lastModified() }.getOrDefault(0L))
            }
        }
    }
}