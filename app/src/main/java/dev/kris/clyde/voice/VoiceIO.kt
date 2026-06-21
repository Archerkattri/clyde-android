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
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null

    // TextToSpeech + SpeechRecognizer are main-thread-affine, but /speak (NanoHTTPD worker) and the
    // brain-event callbacks (IO dispatcher) call in off-main — so every public entry hops to main.
    private val main = Handler(Looper.getMainLooper())
    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    init {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
        }
    }

    fun speak(text: String) = onMain {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "clyde-${System.identityHashCode(text)}")
    }

    fun listen(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit) = onMain {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onError("speech recognition unavailable")
            return@onMain
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) = onError(when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "mic-permission"
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no-speech"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network"
                    else -> "stt-$error"
                })
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.firstText()?.let(onPartial)
                }
                override fun onResults(results: Bundle?) {
                    results?.firstText()?.let(onFinal) ?: onError("no speech")
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

    fun stopListening() = onMain {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
