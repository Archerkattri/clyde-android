package dev.kris.clyde.assist

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.kris.clyde.service.AgentOrchestratorService

/** ACTION_ASSIST entry — transparent. Starts the orchestrator and finishes immediately. */
class AssistEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, AgentOrchestratorService::class.java)
            .setAction(AgentOrchestratorService.ACTION_ASSIST)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        finish()
    }
}
