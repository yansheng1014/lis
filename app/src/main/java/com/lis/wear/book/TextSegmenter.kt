package com.lis.wear.book

/**
 * Splits raw book text into chapters and sentences.
 *
 * Chapter detection is heuristic and tuned for Chinese web novels plus common
 * English patterns:
 *  - 第1章 / 第一百二十三章 / 第 5 节 ...
 *  - Chapter 12 / CHAPTER XII
 *  - 序章 / 楔子 / 前言 / 后记 / 番外
 *
 * If no headings are found we fall back to fixed-size blocks so playback and
 * "next episode" still work on unstructured text.
 */
object TextSegmenter {

    private const val FALLBACK_BLOCK_CHARS = 2500
    private const val MAX_HEADING_LEN = 40
    private const val MIN_CHAPTERS_FOR_HEADINGS = 2

    private val chineseNumeral = "零〇一二三四五六七八九十百千万两"

    private val chapterRegexes = listOf(
        // 第123章 第一百二十三章 第 5 节，允许后面跟标题
        Regex("""^\s*第\s*[0-9$chineseNumeral]{1,12}\s*[章节回卷篇折]\s*[:：、.\-—\s]*(.{0,30})$"""),
        // 序章/楔子/前言/后记/番外/尾声
        Regex("""^\s*(序章|序言|序|楔子|前言|引子|引言|后记|後記|尾声|尾聲|番外|终章|終章|附录|附錄)\s*[:：、.\-—\s]*(.{0,30})$"""),
        // Chapter 12 / CHAPTER XII / Part 3
        Regex("""^\s*(chapter|chap\.?|part|book|section)\s+([0-9]{1,4}|[ivxlcdm]{1,10})\b\s*[:.\-—]?\s*(.{0,40})$""", RegexOption.IGNORE_CASE),
        // 纯数字行标题：123 或 1. 标题
        Regex("""^\s*([0-9]{1,4})\s*[.、:：]\s*(.{0,40})$"""),
    )

    /** Sentence terminators: keep CJK punctuation attached to the sentence. */
    private val sentenceEnders = charArrayOf(
        '。', '！', '？', '；', '…', '.', '!', '?', ';', '\n',
    )
    private val closingPunctuation = charArrayOf(
        '”', '"', '’', '\'', '）', ')', '』', '」', '》', '〉', '】', ']', '…',
    )

    fun isChapterHeading(rawLine: String): Boolean {
        val line = rawLine.trim()
        if (line.isEmpty() || line.length > MAX_HEADING_LEN) return false
        // A heading line should not end with sentence punctuation like 。
        if (line.length > 6 && (line.endsWith("。") || line.endsWith("，"))) return false
        return chapterRegexes.any { it.matches(line) }
    }

    /**
     * Split the whole book text into chapters.
     *
     * @param fallbackTitle used when the text has no detectable headings
     */
    fun splitChapters(text: String, fallbackTitle: String): List<RawChapter> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')

        val chapters = mutableListOf<RawChapter>()
        val buffer = StringBuilder()
        var currentTitle: String? = null

        fun flush() {
            val body = buffer.toString()
            if (currentTitle != null || body.isNotBlank()) {
                chapters += RawChapter(
                    title = currentTitle ?: fallbackTitle,
                    body = body,
                )
            }
            buffer.setLength(0)
        }

        for (line in lines) {
            if (isChapterHeading(line)) {
                flush()
                currentTitle = line.trim()
            } else {
                buffer.append(line).append('\n')
            }
        }
        flush()

        val meaningful = chapters.filter { it.body.isNotBlank() || it.title != fallbackTitle }
        return if (meaningful.count { it.title != fallbackTitle } >= MIN_CHAPTERS_FOR_HEADINGS) {
            meaningful
        } else {
            blockSplit(normalized, fallbackTitle)
        }
    }

    /** Fixed-size fallback so long unstructured text is still navigable. */
    private fun blockSplit(text: String, fallbackTitle: String): List<RawChapter> {
        val clean = text.trim()
        if (clean.isEmpty()) return emptyList()
        if (clean.length <= FALLBACK_BLOCK_CHARS) {
            return listOf(RawChapter(fallbackTitle, clean))
        }
        val out = mutableListOf<RawChapter>()
        var start = 0
        var index = 1
        while (start < clean.length) {
            var end = minOf(start + FALLBACK_BLOCK_CHARS, clean.length)
            if (end < clean.length) {
                // extend to the next sentence break to avoid cutting mid-sentence
                val limit = minOf(end + 400, clean.length)
                var probe = end
                while (probe < limit && clean[probe] !in sentenceEnders) probe++
                if (probe < limit) end = probe + 1
            }
            out += RawChapter("第 $index 段", clean.substring(start, end).trim())
            start = end
            index++
        }
        return out
    }

    /**
     * Split a chapter body into TTS-sized sentences.
     *
     * @param maxLen hard cap so one utterance is never absurdly long
     */
    fun splitSentences(body: String, maxLen: Int = 120): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()

        fun push() {
            val s = sb.toString().trim()
            if (s.isNotEmpty()) out += s
            sb.setLength(0)
        }

        var i = 0
        while (i < body.length) {
            val c = body[i]
            sb.append(c)
            if (c in sentenceEnders) {
                // absorb trailing quotes/brackets and repeated terminators
                var j = i + 1
                while (j < body.length &&
                    (body[j] in closingPunctuation || body[j] in sentenceEnders) &&
                    body[j] != '\n'
                ) {
                    sb.append(body[j])
                    j++
                }
                i = j
                push()
                continue
            }
            if (sb.length >= maxLen) {
                // break on a comma-ish boundary when possible
                val text = sb.toString()
                val cut = text.lastIndexOfAny(charArrayOf('，', ',', '、', '：', ':', '—', ' '))
                if (cut > maxLen / 2) {
                    out += text.substring(0, cut + 1).trim()
                    sb.setLength(0)
                    sb.append(text.substring(cut + 1))
                } else {
                    push()
                }
            }
            i++
        }
        push()
        return out.filter { it.any { ch -> ch.isLetterOrDigit() || ch.code > 0x2000 } }
    }

    data class RawChapter(val title: String, val body: String)
}