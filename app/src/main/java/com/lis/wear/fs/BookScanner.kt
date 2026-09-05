package com.lis.wear.fs

import android.os.Environment
import com.lis.wear.book.BookImporter
import java.io.File

/**
 * Walks common book locations on the watch and returns candidate files.
 *
 * Wear OS stores side-loaded files in a few well-known places; we scan them all
 * (best-effort) rather than relying on a file manager to hand us a single path.
 */
object BookScanner {

    data class ScanResult(val files: List<File>, val error: String?)

    /** Candidate roots in priority order. */
    private fun roots(): List<File> = buildList {
        Environment.getExternalStorageDirectory()?.let { add(it) } // /storage/emulated/0
        runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) }
            .getOrNull()?.let { add(it) }
        runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) }
            .getOrNull()?.let { add(it) }
        // Samsung-specific side-load folders (kept as best-effort probes)
        listOf(
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Books",
            "/storage/emulated/0/Documents",
            "/sdcard/Download",
            "/sdcard/Books",
            "/sdcard/Documents",
        ).mapNotNull { runCatching { File(it) }.getOrNull() }.forEach { add(it) }
    }.distinctBy { it.absolutePath }

    fun scan(maxDepth: Int = 4, limit: Int = 500): ScanResult {
        return try {
            val found = LinkedHashSet<File>()
            for (root in roots()) {
                if (found.size >= limit) break
                walk(root, maxDepth, limit - found.size, found)
            }
            ScanResult(found.toList(), null)
        } catch (t: Throwable) {
            ScanResult(emptyList(), t.message ?: "扫描失败")
        }
    }

    private fun walk(dir: File, depth: Int, budget: Int, out: MutableSet<File>) {
        if (depth < 0 || out.size >= budget) return
        val entries = runCatching { dir.listFiles() }.getOrNull() ?: return
        val (books, subdirs) = entries.partition { it.isFile && BookImporter.isSupported(it.name) }
        books.forEach { if (out.size < budget) out.add(it) }
        subdirs.filter { it.isDirectory }.forEach { walk(it, depth - 1, budget, out) }
    }
}