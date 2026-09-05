package com.lis.wear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lis.wear.model.Book
import com.lis.wear.model.BookMeta
import com.lis.wear.model.BookProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lis_prefs")

/**
 * Single source of truth for the library, per-book progress and TTS settings.
 *
 * Books are stored as JSON under files/books/<id>.json, the index under
 * files/library.json, and small scalars in DataStore.
 */
class LisRepository private constructor(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val libraryMutex = Mutex()

    private val _library = MutableStateFlow<List<BookMeta>>(emptyList())
    val library: StateFlow<List<BookMeta>> = _library.asStateFlow()

    val speed: Flow<Float> = context.dataStore.data.map { it[KEY_SPEED] ?: 1.0f }
    val pitch: Flow<Float> = context.dataStore.data.map { it[KEY_PITCH] ?: 1.0f }
    val lastBookId: Flow<String?> = context.dataStore.data.map { it[KEY_LAST_BOOK] }

    init {
        io.launch { reloadLibrary() }
    }

    suspend fun reloadLibrary() = withContext(Dispatchers.IO) {
        libraryMutex.withLock {
            val file = libraryFile()
            val metas = if (file.exists()) {
                runCatching {
                    json.decodeFromString<List<BookMeta>>(file.readText())
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            // drop entries whose cached book json vanished
            val valid = metas.filter { bookFile(it.id).exists() }
            _library.value = valid.sortedByDescending { it.addedAt }
            if (valid.size != metas.size) persistLibraryLocked(valid)
        }
    }

    suspend fun saveBook(book: Book): BookMeta = withContext(Dispatchers.IO) {
        bookFile(book.id).writeText(json.encodeToString(book))
        val meta = BookMeta(
            id = book.id,
            title = book.title,
            sourcePath = book.sourcePath,
            format = book.format,
            chapterCount = book.chapterCount,
            addedAt = System.currentTimeMillis(),
        )
        libraryMutex.withLock {
            val next = _library.value.filterNot { it.id == meta.id } + meta
            val sorted = next.sortedByDescending { it.addedAt }
            _library.value = sorted
            persistLibraryLocked(sorted)
        }
        meta
    }

    suspend fun loadBook(id: String): Book? = withContext(Dispatchers.IO) {
        val f = bookFile(id)
        if (!f.exists()) return@withContext null
        runCatching { json.decodeFromString<Book>(f.readText()) }.getOrNull()
    }

    suspend fun deleteBook(id: String) = withContext(Dispatchers.IO) {
        bookFile(id).delete()
        libraryMutex.withLock {
            val next = _library.value.filterNot { it.id == id }
            _library.value = next
            persistLibraryLocked(next)
        }
        context.dataStore.edit { prefs ->
            prefs.remove(progressKey(id))
            if (prefs[KEY_LAST_BOOK] == id) prefs.remove(KEY_LAST_BOOK)
        }
        Unit
    }

    suspend fun progressFor(id: String): BookProgress = withContext(Dispatchers.IO) {
        val raw = context.dataStore.data.map { it[progressKey(id)] }.first()
        raw?.let { runCatching { json.decodeFromString<BookProgress>(it) }.getOrNull() }
            ?: BookProgress()
    }

    suspend fun saveProgress(id: String, progress: BookProgress) {
        context.dataStore.edit { prefs ->
            prefs[progressKey(id)] = json.encodeToString(progress)
            prefs[KEY_LAST_BOOK] = id
        }
    }

    suspend fun setSpeed(value: Float) {
        context.dataStore.edit { it[KEY_SPEED] = value.coerceIn(0.4f, 3.0f) }
    }

    suspend fun setPitch(value: Float) {
        context.dataStore.edit { it[KEY_PITCH] = value.coerceIn(0.5f, 2.0f) }
    }

    suspend fun currentSpeed(): Float = speed.first()

    suspend fun currentPitch(): Float = pitch.first()

    suspend fun currentLastBookId(): String? = lastBookId.first()

    private fun persistLibraryLocked(metas: List<BookMeta>) {
        runCatching { libraryFile().writeText(json.encodeToString(metas)) }
    }

    private fun libraryFile() = File(context.filesDir, "library.json")

    private fun bookFile(id: String) =
        File(File(context.filesDir, "books").apply { mkdirs() }, "$id.json")

    companion object {
        private val KEY_SPEED = floatPreferencesKey("tts_speed")
        private val KEY_PITCH = floatPreferencesKey("tts_pitch")
        private val KEY_LAST_BOOK = stringPreferencesKey("last_book_id")
        private fun progressKey(id: String) = stringPreferencesKey("progress_$id")

        @Volatile
        private var instance: LisRepository? = null

        fun get(context: Context): LisRepository =
            instance ?: synchronized(this) {
                instance ?: LisRepository(context.applicationContext).also { instance = it }
            }
    }
}