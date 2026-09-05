package com.lis.wear.book

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lis.wear.model.Book
import com.lis.wear.model.BookFormat
import com.lis.wear.model.Chapter
import java.io.File

/**
 * Turns a file (or content Uri) into a [Book]. Source bytes are copied into app
 * storage first so playback keeps working after external permissions change or
 * the original file goes away.
 */
class BookImporter(private val context: Context) {

    fun importFile(file: File): Book {
        require(file.exists()) { "文件不存在: ${file.name}" }
        val format = formatFor(file.name)
        val id = idFor(file.name, file.length())
        val stored = copyToStorage(file, id)
        return parse(stored, file.nameWithoutExtension, format, id)
    }

    fun importUri(uri: Uri): Book {
        val name = displayName(uri) ?: "book_${System.currentTimeMillis()}"
        val format = formatFor(name)
        val id = idFor(name, 0L)
        val target = File(sourceDir(), "$id.${format.ext()}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: error("无法读取所选文件")
        return parse(target, name.substringBeforeLast('.'), format, id)
    }

    private fun parse(file: File, fallbackTitle: String, format: BookFormat, id: String): Book {
        val (title, rawChapters) = when (format) {
            BookFormat.EPUB -> {
                val doc = EpubReader.read(file)
                doc.title to doc.chapters
            }
            BookFormat.TXT -> {
                val text = file.inputStream().use { CharsetDetector.readText(it) }
                fallbackTitle to TextSegmenter.splitChapters(text, fallbackTitle)
            }
        }
        val chapters = rawChapters
            .map { Chapter(it.title.trim(), TextSegmenter.splitSentences(it.body)) }
            .filter { it.sentences.isNotEmpty() }
        require(chapters.isNotEmpty()) { "没能解析出可朗读的内容" }
        return Book(
            id = id,
            title = title.ifBlank { fallbackTitle },
            sourcePath = file.absolutePath,
            format = format,
            chapters = chapters,
        )
    }

    private fun copyToStorage(file: File, id: String): File {
        val target = File(sourceDir(), "$id.${formatFor(file.name).ext()}")
        if (target.absolutePath == file.absolutePath) return file
        file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }

    private fun sourceDir(): File =
        File(context.filesDir, "sources").apply { mkdirs() }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    companion object {
        fun formatFor(name: String): BookFormat =
            if (name.endsWith(".epub", ignoreCase = true)) BookFormat.EPUB else BookFormat.TXT

        fun isSupported(name: String): Boolean = SUPPORTED_EXT.any {
            name.endsWith(it, ignoreCase = true)
        }

        val SUPPORTED_EXT = listOf(".txt", ".epub", ".text", ".md")

        private fun BookFormat.ext(): String = when (this) {
            BookFormat.EPUB -> "epub"
            BookFormat.TXT -> "txt"
        }

        private fun idFor(name: String, size: Long): String {
            val base = name.substringBeforeLast('.')
                .replace(Regex("""[^\p{L}\p{N}_-]"""), "_")
                .take(32)
            val hash = (name.hashCode().toLong() * 31 + size).toString(16).replace("-", "")
            return "${base}_$hash"
        }
    }
}