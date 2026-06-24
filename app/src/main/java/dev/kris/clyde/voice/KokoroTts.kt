package dev.kris.clyde.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Kokoro voice id → sherpa-onnx speaker id (verified for the kokoro-multi-lang-v1_0 bundle). */
object KokoroVoices {
    // ordered for the picker; af_bella is the default
    val sid = linkedMapOf(
        "af_bella" to 2,
        "af_nicole" to 6,
        "am_adam" to 11,
        "am_santa" to 19,
        "bm_lewis" to 27,
    )
    val label = linkedMapOf(
        "af_bella" to "Bella",
        "af_nicole" to "Nicole",
        "am_adam" to "Adam",
        "am_santa" to "Santa",
        "bm_lewis" to "Lewis · UK",
    )
    fun isKokoro(voice: String) = sid.containsKey(voice)
}

/** Downloads + extracts the Kokoro model bundle (~700 MB) to filesDir on first use. */
object KokoroModel {
    private const val TAG = "KokoroModel"
    const val NAME = "kokoro-multi-lang-v1_0"
    private const val URL_STR =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$NAME.tar.bz2"

    fun dir(ctx: Context): File = File(ctx.filesDir, NAME)

    /** Ready when the model file is present and a plausible size (~310 MB). */
    fun isReady(ctx: Context): Boolean =
        File(dir(ctx), "model.onnx").let { it.exists() && it.length() > 100_000_000L } &&
            File(dir(ctx), "espeak-ng-data").isDirectory

    /** Blocking download + extract. Call OFF the main thread. [onProgress] is 0..1 over the download. */
    @Synchronized
    fun ensure(ctx: Context, onProgress: (Float) -> Unit): Boolean {
        if (isReady(ctx)) return true
        val tmp = File(ctx.cacheDir, "$NAME.tar.bz2")
        try {
            val conn = (URL(URL_STR).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            conn.connect()
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: 700_000_000L
            conn.inputStream.use { input ->
                tmp.outputStream().buffered().use { out ->
                    val buf = ByteArray(1 shl 16)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); read += n
                        onProgress((read.toDouble() / total).coerceIn(0.0, 1.0).toFloat())
                    }
                }
            }
            // extract bz2 -> tar into filesDir (entries are prefixed with "$NAME/")
            BZip2CompressorInputStream(tmp.inputStream().buffered()).use { bz ->
                TarArchiveInputStream(bz).use { tar ->
                    var e = tar.nextEntry
                    while (e != null) {
                        val f = File(ctx.filesDir, e.name)
                        if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { tar.copyTo(it) } }
                        e = tar.nextEntry
                    }
                }
            }
            return isReady(ctx)
        } catch (e: Exception) {
            Log.e(TAG, "model download/extract failed", e)
            return false
        } finally {
            tmp.delete()
        }
    }
}

/**
 * On-device Kokoro neural TTS: synthesizes with sherpa-onnx [OfflineTts] on a worker thread and plays
 * the float PCM via [AudioTrack]. Mirrors the engine contract VoiceIO needs: speak(text, onDone) where
 * onDone runs once when playback finishes naturally (not on stop), so auto-listen continues a turn.
 */
class KokoroTts private constructor(private val tts: OfflineTts) {
    @Volatile var sid: Int = 2 // af_bella
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var track: AudioTrack? = null
    @Volatile private var gen = 0 // bumped on every new utterance / stop → cancels in-flight work

    /** Speak [text]; [onDone] runs once on natural completion. A new speak() or stop() cancels prior. */
    fun speak(text: String, onDone: () -> Unit) {
        val myGen = ++gen
        releaseTrack()
        if (text.isBlank()) { main.post(onDone); return }
        worker.execute {
            if (myGen != gen) return@execute
            val audio: GeneratedAudio? = runCatching { tts.generate(text, sid, 1.0f) }.getOrNull()
            if (audio == null || myGen != gen) return@execute
            main.post { if (myGen == gen) play(audio.samples, audio.sampleRate, myGen, onDone) }
        }
    }

    private fun play(samples: FloatArray, sr: Int, myGen: Int, onDone: () -> Unit) {
        if (samples.isEmpty()) { onDone(); return }
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * 4)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        t.setNotificationMarkerPosition(samples.size)
        t.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(at: AudioTrack?) {
                runCatching { at?.release() }
                if (myGen == gen) { track = null; main.post(onDone) }
            }
            override fun onPeriodicNotification(at: AudioTrack?) {}
        })
        track = t
        t.play()
    }

    private fun releaseTrack() {
        val t = track; track = null
        runCatching { t?.pause(); t?.flush(); t?.release() }
    }

    /** Interrupt playback immediately; no onDone fires. */
    fun stop() { gen++; releaseTrack() }

    fun shutdown() {
        stop()
        runCatching { worker.shutdownNow() }
        runCatching { tts.release() }
    }

    companion object {
        private const val TAG = "KokoroTts"
        /** Build an engine if the model is ready, else null (caller falls back to system TTS). */
        fun create(ctx: Context): KokoroTts? {
            if (!KokoroModel.isReady(ctx)) return null
            val root = KokoroModel.dir(ctx).absolutePath
            val cfg = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(
                        model = "$root/model.onnx",
                        voices = "$root/voices.bin",
                        tokens = "$root/tokens.txt",
                        dataDir = "$root/espeak-ng-data",
                        lexicon = "$root/lexicon-us-en.txt,$root/lexicon-gb-en.txt",
                        lengthScale = 1.0f,
                    ),
                    numThreads = 2,
                    provider = "cpu",
                ),
                maxNumSentences = 1,
            )
            return runCatching { KokoroTts(OfflineTts(assetManager = null, config = cfg)) }
                .onFailure { Log.e(TAG, "OfflineTts init failed", it) }
                .getOrNull()
        }
    }
}
