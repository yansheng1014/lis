package com.lis.wear.model

import kotlinx.serialization.Serializable

/** Supported source formats. */
enum class BookFormat { TXT, EPUB }

/** A chapter is a title plus the sentences we hand to TTS one by one. */
@Serializable
data class Chapter(
    val title: String,
    val sentences: List<String>,
) {
    val charCount: Int get() = sentences.sumOf { it.length }
}

/** Fully parsed book, cached as JSON in app storage. */
@Serializable
data class Book(
    val id: String,
    val title: String,
    val sourcePath: String,
    val format: BookFormat,
    val chapters: List<Chapter>,
) {
    val chapterCount: Int get() = chapters.size
}

/** Lightweight entry for the library list (avoids loading every book). */
@Serializable
data class BookMeta(
    val id: String,
    val title: String,
    val sourcePath: String,
    val format: BookFormat,
    val chapterCount: Int,
    val addedAt: Long,
)

@Serializable
data class BookProgress(
    val chapterIndex: Int = 0,
    val sentenceIndex: Int = 0,
)

/** Sleep-timer selection. */
enum class SleepTimer(val minutes: Int, val label: String) {
    OFF(0, "关闭"),
    M15(15, "15 分钟"),
    M30(30, "30 分钟"),
    M60(60, "1 小时"),
    M90(90, "1.5 小时"),
    CHAPTER_END(-1, "本章结束"),
}

/** What the UI renders; a flattened snapshot of the playback engine. */
data class PlayerState(
    val bookId: String? = null,
    val bookTitle: String = "",
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    val chapterTitle: String = "",
    val chapterTitles: List<String> = emptyList(),
    val sentenceIndex: Int = 0,
    val sentences: List<String> = emptyList(),
    val isPlaying: Boolean = false,
    val isPreparing: Boolean = false,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val sleepTimer: SleepTimer = SleepTimer.OFF,
    val sleepTimerEndsAt: Long = 0L,
    val error: String? = null,
) {
    val currentSentence: String
        get() = sentences.getOrNull(sentenceIndex).orEmpty()

    val hasBook: Boolean get() = bookId != null

    val sentenceTotal: Int get() = sentences.size

    /** 0f..1f progress inside the current chapter. */
    val chapterProgress: Float
        get() = if (sentences.isEmpty()) 0f
        else ((sentenceIndex + 1).toFloat() / sentences.size).coerceIn(0f, 1f)

    fun titleForChapter(index: Int): String =
        chapterTitles.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "第 ${index + 1} 章"
}