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
import android.speech.tts.UtteranceProgressListener
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
    // Fired once when the current utterance finishes naturally (not on stop) — drives "listen again
    // after Clyde speaks" so a conversation continues without re-summoning. Cleared on stop/interrupt.
    @Volatile private var speakDone: (() -> Unit)? = null
    // Streaming TTS: an answer may be spoken as an early first sentence (QUEUE_FLUSH, no continuation)
    // then its remainder (QUEUE_ADD). Only the utterance whose id equals this one runs speakDone, so
    // auto-listen fires exactly once — after the WHOLE answer has been spoken, never after the early bit.
    @Volatile private var finalUtteranceId: String? = null
    private var utterSeq = 0

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
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            // Natural completion → run the one-shot continuation (auto-listen). stop()/interrupt clears
            // speakDone first, so an interrupted utterance never auto-listens.
            override fun onDone(utteranceId: String?) {
                if (utteranceId == null || utteranceId != finalUtteranceId) return // early chunk → no continuation
                finalUtteranceId = null
                val d = speakDone; speakDone = null
                if (d != null) main.post { d() }
            }
            @Deprecated("kept for older engines") override fun onError(utteranceId: String?) { speakDone = null; finalUtteranceId = null }
            override fun onError(utteranceId: String?, errorCode: Int) { speakDone = null; finalUtteranceId = null }
        })
    }

    /** Speak [text]; [onDone] runs once if the utterance finishes naturally (not if interrupted). The
     *  utteranceId must be non-null for the progress listener to fire onDone. */
    fun speak(text: String, onDone: () -> Unit = {}) = onMain {
        speakDone = onDone
        val id = "final-${utterSeq++}"
        finalUtteranceId = id
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    /** Streaming TTS — speak the first sentence the instant it's ready (flushes prior speech). No
     *  continuation rides this chunk; the remainder carries it, so auto-listen never fires early. */
    fun speakEarly(text: String) = onMain {
        if (text.isBlank()) return@onMain
        finalUtteranceId = null
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "early-${utterSeq++}")
    }

    /** Streaming TTS — speak the rest of the answer QUEUED after the early sentence; [onDone]
     *  (auto-listen) fires when THIS finishes, i.e. after the whole answer has been spoken. */
    fun speakRemainder(text: String, onDone: () -> Unit = {}) = onMain {
        if (text.isBlank()) { onDone(); return@onMain }
        speakDone = onDone
        val id = "final-${utterSeq++}"
        finalUtteranceId = id
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id)
    }

    /** Interrupt any ongoing speech immediately (tapping mic / sending a new message / dismissing).
     *  Clears the pending continuation so an interrupted answer doesn't also auto-listen. */
    fun stopSpeaking() = onMain { speakDone = null; finalUtteranceId = null; tts?.stop() }

    fun listen(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit, onRms: (Float) -> Unit = {}) = onMain {
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
                // Mic level for the live voice light. rmsdB runs roughly -2 (silence) .. 10 (loud);
                // normalize to 0..1 so the UI reacts to the user's actual voice, not a canned animation.
                override fun onRmsChanged(rmsdB: Float) {
                    if (sessionActive) onRms(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (!sessionActive) return // a cancel WE initiated — don't fire (would re-show the overlay)
                    sessionActive = false
                    onError(when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "mic-permission"
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no-speech"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy" // a prior session not yet released
                        else -> "stt-$error"
                    })
                }
                // Live transcript only — surfaced to the UI as you speak. We do NOT act on partials or
                // finalize early; the recognizer's own end-of-speech (the silence windows below) decides
                // when you're done, so Clyde starts working only once you've actually stopped talking.
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            // Be patient: don't end the turn the instant the user pauses, and require a little speech
            // before giving up — far fewer spurious "didn't catch that". (OEMs honor these to varying
            // degrees, but they never hurt.) NO EXTRA_PREFER_OFFLINE: forcing offline fails outright
            // when the locale's offline model isn't installed — a big source of instant no-match.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500)
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
        speakDone = null
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
