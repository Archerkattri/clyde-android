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
        private const val CHANNEL = "clyde_orchestrator"
        private const val NOTIF_ID = 42
        private const val TAG = "Clyde"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var voice: VoiceIO
    private lateinit var overlay: OverlayController
    private var server: LocalControlServer? = null
    private val sessionId = "app-${System.currentTimeMillis()}"

    override fun onCreate() {
        super.onCreate()
        voice = VoiceIO(this)
        overlay = OverlayController(applicationContext)
        startInForeground()
        startServer()
    }

    private fun startServer() {
        if (server != null) return
        try {
            server = LocalControlServer(
                ctx = applicationContext,
                voice = voice,
                key = Prefs.clydeKey,
                confirmHandler = { summary, details -> overlay.confirmBlocking(summary, details) },
                overlayStatus = { text, _ -> overlay.status(text) },
            ).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
            Log.i(TAG, "LocalControlServer up on 127.0.0.1:${LocalControlServer.PORT}")
        } catch (e: Exception) {
            Log.e(TAG, "LocalControlServer failed to start", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ASSIST) beginAssist()
        return START_STICKY
    }

    private fun beginAssist() {
        overlay.showSummon()
        voice.listen(
            onPartial = { overlay.transcript(it) },
            onFinal = { text -> if (text.isNotBlank()) { overlay.transcript(text); handle(text) } },
            onError = { overlay.status("Didn't catch that"); Log.w(TAG, "STT: $it") },
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
                    "error" -> ev.optString("text").let { overlay.answer("Sorry — $it"); voice.speak("Sorry, $it") }
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
            Log.e(TAG, "startForeground failed", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        server?.stop()
        voice.destroy()
        overlay.destroy()
        scope.cancel()
        super.onDestroy()
    }
}
