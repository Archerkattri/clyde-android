package dev.kris.clyde.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/** STT (SpeechRecognizer) + TTS (TextToSpeech). Create/call from the main thread. */
class VoiceIO(private val ctx: Context) {
    private companion object { const val GOOGLE_TTS = "com.google.android.tts" }

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    // True only while a listen cycle the user actually started is in flight. A stop/cancel WE trigger
    // (dismiss / send / kill) clears it so the recognizer's late callbacks are ignored — otherwise a
    // cancellation error re-ran onError → re-attached the overlay ("popup won't close").
    @Volatile private var sessionActive = false

    // TextToSpeech + SpeechRecognizer are main-thread-affine, but /speak (NanoHTTPD worker) and the
    // brain-event callbacks (IO dispatcher) call in off-main — so every public entry hops to main.
    private val main = Handler(Looper.getMainLooper())
    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    init {
        initTts(GOOGLE_TTS) // prefer Google's engine; falls back to the device default if absent
    }

    private fun initTts(engine: String?) {
        tts = TextToSpeech(ctx, { status ->
            when {
                status == TextToSpeech.SUCCESS -> configureTts()
                engine != null -> { tts?.shutdown(); initTts(null) } // Google engine failed → default
            }
        }, engine)
    }

    /** Use Assistant audio attrs + the highest-quality OFFLINE neural voice for the locale. The good
     *  Google voices report QUALITY_VERY_HIGH and need no network — top-tier voices, no API key, no
     *  billing (the only voice path compatible with Clyde's subscription-only rule). */
    private fun configureTts() {
        val t = tts ?: return
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        val locale = Locale.getDefault()
        val best = runCatching {
            t.voices
                ?.filter { it.locale?.language == locale.language }
                ?.filter { !it.isNetworkConnectionRequired }
                ?.filter { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
                ?.maxByOrNull { it.quality }
        }.getOrNull()
        if (best != null) t.voice = best else t.language = locale
    }

    fun speak(text: String) = onMain {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "clyde-${System.identityHashCode(text)}")
    }

    /** Interrupt any ongoing speech immediately (tapping mic / sending a new message / dismissing). */
    fun stopSpeaking() = onMain { tts?.stop() }

    fun listen(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit) = onMain {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onError("speech recognition unavailable")
            return@onMain
        }
        recognizer?.destroy()
        sessionActive = true
        recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (!sessionActive) return // a cancel WE initiated — don't fire (would re-show the overlay)
                    sessionActive = false
                    onError(when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "mic-permission"
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no-speech"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network"
                        else -> "stt-$error"
                    })
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (!sessionActive) return
                    partialResults?.firstText()?.let(onPartial)
                }
                override fun onResults(results: Bundle?) {
                    if (!sessionActive) return
                    sessionActive = false
                    results?.firstText()?.let(onFinal) ?: onError("no-speech")
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        recognizer?.startListening(intent)
    }

    private fun Bundle.firstText(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    /** Stop + DISCARD the current recognition (fires no onFinal/onError) — used on dismiss/send/kill so
     *  a late cancellation callback can't re-attach the overlay (the "popup won't close" bug). */
    fun stopListening() = onMain {
        sessionActive = false
        recognizer?.cancel()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
