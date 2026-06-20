package dev.kris.clyde.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.kris.clyde.R
import dev.kris.clyde.bridge.BrainClient
import dev.kris.clyde.bridge.LocalControlServer
import dev.kris.clyde.overlay.OverlayController
import dev.kris.clyde.runtime.BrainRunner
import dev.kris.clyde.runtime.EmbeddedRuntime
import dev.kris.clyde.util.Prefs
import dev.kris.clyde.voice.VoiceIO
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Session owner: hosts LocalControlServer (8766), runs voice, talks to the brain. */
class AgentOrchestratorService : Service() {

    companion object {
        const val ACTION_ASSIST = "dev.kris.clyde.ASSIST"
        const val ACTION_KILL = "dev.kris.clyde.KILL"
        private const val CHANNEL = "clyde_orchestrator"
        private const val NOTIF_ID = 42
        private const val TAG = "Clyde"
        private const val BRAIN_VERSION = "0.1.0" // bump to force the embedded runtime to re-extract
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var voice: VoiceIO
    private lateinit var overlay: OverlayController
    private var server: LocalControlServer? = null
    private val brain by lazy { BrainRunner(applicationContext) }
    private val sessionId = "app-${System.currentTimeMillis()}"

    override fun onCreate() {
        super.onCreate()
        voice = VoiceIO(this)
        overlay = OverlayController(applicationContext)
        startInForeground()
        startServer()
        maybeStartEmbeddedBrain()
    }

    /** If this build bundles the embedded runtime, extract it once and run the brain in-process.
     *  No-op on builds without a bundled bootstrap — those use an external Termux brain instead. */
    private fun maybeStartEmbeddedBrain() {
        if (!EmbeddedRuntime.isBundled(applicationContext)) return
        scope.launch(Dispatchers.IO) {
            if (EmbeddedRuntime.ensureInstalled(applicationContext, BRAIN_VERSION)) {
                brain.start(Prefs.clydeKey)
            }
        }
    }

    private fun startServer() {
        if (server != null) return
        try {
            server = LocalControlServer(
                ctx = applicationContext,
                voice = voice,
                key = Prefs.clydeKey,
                confirmHandler = { summary, details, action, params -> overlay.confirmBlocking(summary, details, action, params) },
                overlayStatus = { text, _ -> overlay.status(text) },
                validateIntentToken = { token, action, body -> overlay.validateIssuedToken(token, action, body) },
                invalidateIntentToken = { token -> overlay.invalidateIssuedToken(token) },
            ).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
            Log.i(TAG, "LocalControlServer up on 127.0.0.1:${LocalControlServer.PORT}")
        } catch (e: Exception) {
            Log.e(TAG, "LocalControlServer failed to start", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ASSIST -> beginAssist()
            ACTION_KILL -> {
                overlay.cancelPendingConfirm() // unpark any blocked /confirm worker so later turns aren't auto-denied
                overlay.clearIssuedTokens()
                overlay.hide()
                voice.stopListening()
            }
        }
        return START_STICKY
    }

    private fun beginAssist() {
        overlay.showSummon()
        voice.listen(
            onPartial = { overlay.transcript(it) },
            onFinal = { text -> if (text.isNotBlank()) { overlay.transcript(text); handle(text) } },
            onError = { overlay.status("Didn't catch that"); Log.w(TAG, "Speech recognition error: $it") },
        )
    }

    private fun handle(text: String) {
        overlay.status("Thinking…")
        scope.launch {
            BrainClient.query(text, sessionId) { ev ->
                when (ev.optString("type")) {
                    "status" -> overlay.status(ev.optString("text"))
                    "action" -> overlay.status(ev.optString("summary").ifBlank { ev.optString("tool") })
                    "final" -> ev.optString("text").let { overlay.answer(it); voice.speak(it) }
                    "error" -> {
                        Log.w(TAG, "brain error: ${ev.optString("text")}")
                        val msg = "Sorry, something went wrong — try again."
                        overlay.answer(msg); voice.speak(msg)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Clyde", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Clyde")
            .setContentText("Assistant ready")
            .setSmallIcon(R.drawable.ic_clyde_logo)
            .setOngoing(true)
            .build()

        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notif, type)
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (e: Exception) {
            // A microphone-typed FGS can throw if there's no while-in-use window. We MUST still
            // fulfil the startForeground contract → retry as SPECIAL_USE only (no such requirement).
            Log.w(TAG, "typed startForeground failed; retrying SPECIAL_USE only", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIF_ID, notif)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "startForeground failed entirely", e2)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        overlay.cancelPendingConfirm() // release a parked confirm worker before closing the server
        brain.stop()
        server?.stop()
        voice.destroy()
        overlay.destroy()
        scope.cancel()
        super.onDestroy()
    }
}
