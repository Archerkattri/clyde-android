package dev.kris.clyde.wake

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.kris.clyde.R
import dev.kris.clyde.service.AgentOrchestratorService
import dev.kris.clyde.util.Prefs
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/**
 * "Hey Clyde" on-device wake word via Vosk (offline, Apache-2.0). OFF by default and fully isolated —
 * when disabled, nothing here runs. It holds the mic only while enabled and PAUSES whenever Clyde is in
 * an active voice session (the orchestrator owns the mic then), to avoid contention. The ~40 MB English
 * model is downloaded on first enable so the APK stays lean.
 *
 * Platform limit (documented, not a bug): a microphone foreground service can't be (re)started from the
 * background/boot on Android 12+, so after a reboot the user must open Clyde once to re-arm the hotword.
 */
class WakeWordService : Service() {

    companion object {
        private const val TAG = "ClydeWake"
        private const val CHANNEL = "clyde_wake"
        private const val NOTIF_ID = 73
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        private const val MODEL_DIR = "vosk-model-small-en-us-0.15" // top-level folder inside the zip
        // Wake phrases; "[unk]" lets Vosk reject everything else (keyword spotting). A few near-homophones
        // of "Clyde" are included because small models often hear it as "clive"/"clyne".
        private const val GRAMMAR = "[\"hey clyde\", \"hi clyde\", \"okay clyde\", \"hey clive\", \"hi clive\", \"[unk]\"]"
        private const val SAMPLE_RATE = 16000.0f

        @Volatile var instance: WakeWordService? = null

        /** Start the wake service IF the user enabled it. Must be called from a foreground context on
         *  Android 12+ (a mic FGS can't start from the background) — caught + ignored otherwise. */
        fun startIfEnabled(ctx: Context) {
            if (!Prefs.wakeWord) return
            val i = Intent(ctx, WakeWordService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
            }.onFailure { Log.w(TAG, "couldn't start wake service (likely background-start restriction)", it) }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, WakeWordService::class.java)) }
        }

        /** Pause/resume hotword capture while Clyde itself holds the mic (mic-contention guard). */
        fun pause() { instance?.setPaused(true) }
        fun resume() { instance?.setPaused(false) }
    }

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var speech: SpeechService? = null
    @Volatile private var model: Model? = null
    @Volatile private var paused = false
    @Volatile private var triggering = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundCompat("Starting “Hey Clyde”…")
        io.execute { setup() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun setup() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateNotif("Wake word needs mic access"); return
        }
        try {
            val dir = File(filesDir, MODEL_DIR)
            if (!File(dir, "am").exists()) {
                updateNotif("Downloading voice model…")
                downloadAndUnpackModel()
            }
            if (!File(dir, "am").exists()) { updateNotif("Wake word unavailable (model)"); return }
            val m = Model(dir.absolutePath)
            model = m
            val rec = Recognizer(m, SAMPLE_RATE, GRAMMAR)
            val ss = SpeechService(rec, SAMPLE_RATE)
            speech = ss
            ss.startListening(listener)
            updateNotif("Listening for “Hey Clyde”")
            Log.i(TAG, "wake word listening")
        } catch (t: Throwable) {
            Log.e(TAG, "wake setup failed", t)
            updateNotif("Wake word unavailable")
        }
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) = maybeWake(hypothesis, "partial")
        override fun onResult(hypothesis: String?) = maybeWake(hypothesis, "text")
        override fun onFinalResult(hypothesis: String?) = maybeWake(hypothesis, "text")
        override fun onError(e: Exception?) { Log.w(TAG, "vosk error", e) }
        override fun onTimeout() {}
    }

    private fun maybeWake(hypothesis: String?, field: String) {
        if (hypothesis == null || paused || triggering) return
        val said = runCatching { JSONObject(hypothesis).optString(field) }.getOrDefault("").lowercase()
        if (!said.contains("clyde") && !said.contains("clive")) return
        triggering = true
        Log.i(TAG, "wake phrase heard: \"$said\"")
        // Free the mic for the command, then hand off exactly like the assist gesture.
        setPaused(true)
        runCatching {
            val i = Intent(this, AgentOrchestratorService::class.java).setAction(AgentOrchestratorService.ACTION_ASSIST)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        }.onFailure { Log.w(TAG, "couldn't hand off to assistant", it) }
        // The orchestrator calls resume() when its overlay is dismissed (the normal path). This is just a
        // safety net to re-arm if that never happens; long enough that it won't fight a live session.
        main.postDelayed({ triggering = false; if (Prefs.wakeWord) setPaused(false) }, 25_000)
    }

    fun setPaused(p: Boolean) {
        paused = p
        runCatching { speech?.setPause(p) }
    }

    private fun downloadAndUnpackModel() {
        val conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000; readTimeout = 60_000; instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "model download HTTP ${conn.responseCode}"); return
            }
            val rootPath = filesDir.canonicalPath
            ZipInputStream(BufferedInputStream(conn.inputStream)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val out = File(filesDir, entry.name)
                    // zip-slip guard: never write outside filesDir
                    if (!out.canonicalPath.startsWith(rootPath)) { entry = zis.nextEntry; continue }
                    if (entry.isDirectory) out.mkdirs()
                    else { out.parentFile?.mkdirs(); FileOutputStream(out).use { zis.copyTo(it) } }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            Log.i(TAG, "model unpacked to ${filesDir}/$MODEL_DIR")
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun startForegroundCompat(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Hey Clyde", NotificationManager.IMPORTANCE_MIN))
        }
        val n = notif(text)
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && micGranted) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            Log.w(TAG, "wake startForeground failed", e)
            runCatching { startForeground(NOTIF_ID, n) }
        }
    }

    private fun notif(text: String) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_clyde_logo)
        .setContentTitle("Clyde")
        .setContentText(text)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()

    private fun updateNotif(text: String) = runCatching {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notif(text))
    }

    override fun onDestroy() {
        instance = null
        runCatching { speech?.stop(); speech?.shutdown() }
        speech = null
        runCatching { model?.close() }
        model = null
        io.shutdownNow()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
