package com.lis.wear.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Thin wrapper around the platform TTS engine (Samsung watches ship one).
 *
 * The engine is intentionally "one sentence at a time": we queue a single
 * utterance, and when it finishes the caller decides what comes next. That keeps
 * the resume point exact — we always know which sentence is being spoken.
 */
class TtsEngine(private val context: Context) {

    interface Callbacks {
        /** Called on the TTS thread when an utterance finished normally. */
        fun onUtteranceDone(id: String)

        /** Called when the engine reports an error for an utterance. */
        fun onUtteranceError(id: String)

        /** Called once the engine is usable (or failed to init). */
        fun onReady(success: Boolean)
    }

    private var tts: TextToSpeech? = null
    private var ready = false
    private var callbacks: Callbacks? = null
    private var pendingSpeed = 1.0f
    private var pendingPitch = 1.0f

    val isReady: Boolean get() = ready

    fun setCallbacks(cb: Callbacks?) {
        callbacks = cb
    }

    /** Initialise the engine; safe to call repeatedly. */
    suspend fun init(): Boolean = suspendCancellableCoroutine { cont ->
        if (ready) {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }
        val engine = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) applyParams()
            callbacks?.onReady(ready)
            if (cont.isActive) cont.resume(ready)
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { callbacks?.onUtteranceDone(it) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { callbacks?.onUtteranceError(it) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w(TAG, "utterance error $utteranceId code=$errorCode")
                utteranceId?.let { callbacks?.onUtteranceError(it) }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) = Unit
        })
        tts = engine
        cont.invokeOnCancellation { shutdown() }
    }

    /** Choose a voice that matches the sentence language; falls back silently. */
    fun applyLanguageFor(text: String) {
        val engine = tts ?: return
        val hasCjk = text.any { it.code in 0x4E00..0x9FFF }
        val target = if (hasCjk) Locale.SIMPLIFIED_CHINESE else Locale.getDefault()
        val result = engine.isLanguageAvailable(target)
        if (result >= TextToSpeech.LANG_AVAILABLE) {
            engine.language = target
        }
    }

    fun setSpeed(value: Float) {
        pendingSpeed = value.coerceIn(0.4f, 3.0f)
        if (ready) tts?.setSpeechRate(pendingSpeed)
    }

    fun setPitch(value: Float) {
        pendingPitch = value.coerceIn(0.5f, 2.0f)
        if (ready) tts?.setPitch(pendingPitch)
    }

    private fun applyParams() {
        tts?.setSpeechRate(pendingSpeed)
        tts?.setPitch(pendingPitch)
    }

    /**
     * Speak one sentence, flushing whatever was queued.
     * @return true when the engine accepted the request
     */
    fun speak(text: String, utteranceId: String): Boolean {
        val engine = tts ?: return false
        if (!ready) return false
        applyLanguageFor(text)
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        return result == TextToSpeech.SUCCESS
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }

    companion object {
        private const val TAG = "LisTts"
    }
}