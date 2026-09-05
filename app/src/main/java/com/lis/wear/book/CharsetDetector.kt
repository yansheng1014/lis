package com.lis.wear.book

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Detects the charset of a raw text file. Wear watches have no file manager, so
 * users side-load whatever they have: UTF-8, UTF-16 with BOM, or GB18030 dumps
 * from Chinese novel sites.
 */
object CharsetDetector {

    private val GB18030: Charset? = runCatching { Charset.forName("GB18030") }.getOrNull()
    private val BIG5: Charset? = runCatching { Charset.forName("BIG5") }.getOrNull()

    fun readText(input: InputStream): String {
        val bytes = input.use { it.readBytesCompat() }
        return decode(bytes)
    }

    fun decode(bytes: ByteArray): String {
        bomCharset(bytes)?.let { (charset, offset) ->
            return String(bytes, offset, bytes.size - offset, charset)
        }
        if (isValidUtf8(bytes)) return String(bytes, Charsets.UTF_8)

        // Score legacy Chinese encodings by how many CJK chars they produce.
        val candidates = listOfNotNull(GB18030, BIG5, Charsets.ISO_8859_1)
        var best: Pair<String, Int>? = null
        val sample = if (bytes.size > 64 * 1024) bytes.copyOf(64 * 1024) else bytes
        for (cs in candidates) {
            val decoded = runCatching { String(sample, cs) }.getOrNull() ?: continue
            val score = decoded.count { it.isCjk() } - decoded.count { it == '\uFFFD' } * 4
            if (best == null || score > best!!.second) best = cs.name() to score
        }
        val chosen = best?.first?.let { runCatching { Charset.forName(it) }.getOrNull() }
            ?: Charsets.UTF_8
        return String(bytes, chosen)
    }

    private fun Char.isCjk(): Boolean = code in 0x4E00..0x9FFF || code in 0x3000..0x303F

    private fun bomCharset(bytes: ByteArray): Pair<Charset, Int>? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return Charsets.UTF_8 to 3
        }
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return Charsets.UTF_16LE to 2
            }
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return Charsets.UTF_16BE to 2
            }
        }
        return null
    }

    /** Strict UTF-8 validation over a sample of the payload. */
    private fun isValidUtf8(bytes: ByteArray): Boolean {
        val limit = minOf(bytes.size, 128 * 1024)
        var i = 0
        while (i < limit) {
            val b = bytes[i].toInt() and 0xFF
            val extra = when {
                b <= 0x7F -> 0
                b in 0xC2..0xDF -> 1
                b in 0xE0..0xEF -> 2
                b in 0xF0..0xF4 -> 3
                else -> return false
            }
            if (i + extra >= limit) return true // truncated sample, accept
            for (k in 1..extra) {
                val c = bytes[i + k].toInt() and 0xFF
                if (c < 0x80 || c > 0xBF) return false
            }
            i += extra + 1
        }
        return true
    }

    private fun InputStream.readBytesCompat(): ByteArray {
        val out = ByteArrayOutputStream(maxOf(64, available()))
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}