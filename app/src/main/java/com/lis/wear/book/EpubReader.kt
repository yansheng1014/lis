package com.lis.wear.book

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Minimal EPUB 2/3 reader built on java.util.zip — no third-party dependency,
 * which keeps the watch APK small.
 *
 * Flow: META-INF/container.xml -> OPF (manifest + spine) -> XHTML files in
 * spine order. Each spine document becomes one chapter; if a document is very
 * long we fall back to heading detection inside it.
 */
object EpubReader {

    data class EpubDoc(val title: String, val chapters: List<TextSegmenter.RawChapter>)

    private const val LONG_DOC_CHARS = 12_000

    fun read(file: File): EpubDoc {
        ZipFile(file).use { zip ->
            val opfPath = findOpfPath(zip) ?: error("EPUB 缺少 container.xml")
            val opfXml = zip.readEntryText(opfPath) ?: error("EPUB 缺少 OPF")
            val baseDir = opfPath.substringBeforeLast('/', "")

            val title = Regex("""<dc:title[^>]*>([\s\S]*?)</dc:title>""", RegexOption.IGNORE_CASE)
                .find(opfXml)?.groupValues?.get(1)?.let(::unescapeXml)?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: file.nameWithoutExtension

            val manifest = parseManifest(opfXml)
            val spine = parseSpine(opfXml)
            val order = spine.mapNotNull { manifest[it] }
                .ifEmpty { manifest.values.filter { it.endsWith(".xhtml") || it.endsWith(".html") } }

            val chapters = mutableListOf<TextSegmenter.RawChapter>()
            for ((index, href) in order.withIndex()) {
                val entryPath = resolve(baseDir, href)
                val html = zip.readEntryText(entryPath) ?: continue
                val docTitle = htmlTitle(html) ?: "第 ${index + 1} 章"
                val text = htmlToText(html)
                if (text.isBlank()) continue
                if (text.length > LONG_DOC_CHARS) {
                    chapters += TextSegmenter.splitChapters(text, docTitle)
                } else {
                    chapters += TextSegmenter.RawChapter(docTitle, text)
                }
            }
            return EpubDoc(title, chapters)
        }
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val container = zip.readEntryText("META-INF/container.xml")
        if (container != null) {
            Regex("""full-path\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
                .find(container)?.groupValues?.get(1)?.let { return it }
        }
        // Some malformed EPUBs omit container.xml
        return zip.entries().asSequence()
            .map(ZipEntry::getName)
            .firstOrNull { it.endsWith(".opf", ignoreCase = true) }
    }

    /** id -> href */
    private fun parseManifest(opf: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val itemRegex = Regex("""<item\b[^>]*>""", RegexOption.IGNORE_CASE)
        for (m in itemRegex.findAll(opf)) {
            val tag = m.value
            val id = attr(tag, "id") ?: continue
            val href = attr(tag, "href") ?: continue
            val mediaType = attr(tag, "media-type").orEmpty()
            if (mediaType.contains("html", ignoreCase = true) ||
                href.endsWith(".xhtml", true) || href.endsWith(".html", true) ||
                href.endsWith(".htm", true)
            ) {
                out[id] = href
            }
        }
        return out
    }

    /** ordered idrefs */
    private fun parseSpine(opf: String): List<String> {
        val spineBlock = Regex("""<spine\b[^>]*>([\s\S]*?)</spine>""", RegexOption.IGNORE_CASE)
            .find(opf)?.groupValues?.get(1) ?: return emptyList()
        return Regex("""<itemref\b[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(spineBlock)
            .mapNotNull { attr(it.value, "idref") }
            .toList()
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(tag)?.groupValues?.get(1)

    private fun resolve(baseDir: String, href: String): String {
        val cleaned = href.substringBefore('#')
        val joined = if (baseDir.isEmpty()) cleaned else "$baseDir/$cleaned"
        // normalize ../ segments
        val parts = ArrayDeque<String>()
        for (seg in joined.split('/')) {
            when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(seg)
            }
        }
        return parts.joinToString("/")
    }

    private fun ZipFile.readEntryText(path: String): String? {
        val entry = getEntry(path)
            ?: entries().asSequence().firstOrNull { it.name.equals(path, ignoreCase = true) }
            ?: entries().asSequence().firstOrNull { it.name.endsWith(path.substringAfterLast('/')) }
            ?: return null
        return getInputStream(entry).use { CharsetDetector.readText(it) }
    }

    private fun htmlTitle(html: String): String? {
        for (pattern in listOf(
            """<h1[^>]*>([\s\S]*?)</h1>""",
            """<h2[^>]*>([\s\S]*?)</h2>""",
            """<title[^>]*>([\s\S]*?)</title>""",
        )) {
            val raw = Regex(pattern, RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            val text = raw?.let { stripTags(it) }?.let(::unescapeXml)?.trim()
            if (!text.isNullOrEmpty() && text.length <= 60) return text
        }
        return null
    }

    fun htmlToText(html: String): String {
        var s = html
        s = Regex("""<(script|style|head)\b[\s\S]*?</\1>""", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE).replace(s, "\n")
        s = Regex("""</(p|div|h[1-6]|li|tr|section|blockquote)>""", RegexOption.IGNORE_CASE)
            .replace(s, "\n")
        s = stripTags(s)
        s = unescapeXml(s)
        return s.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun stripTags(s: String): String = Regex("""<[^>]+>""").replace(s, "")

    private fun unescapeXml(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&#160;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&mdash;", "—")
        .replace("&hellip;", "…")
        .replace("&amp;", "&")
        .let { text ->
            Regex("""&#(\d{2,6});""").replace(text) { m ->
                m.groupValues[1].toIntOrNull()?.takeIf { it in 1..0x10FFFF }
                    ?.let { String(Character.toChars(it)) } ?: m.value
            }
        }
        .let { text ->
            Regex("""&#x([0-9a-fA-F]{2,6});""").replace(text) { m ->
                m.groupValues[1].toIntOrNull(16)?.takeIf { it in 1..0x10FFFF }
                    ?.let { String(Character.toChars(it)) } ?: m.value
            }
        }
}