package dev.kris.clyde.runtime

import android.content.Context
import java.io.File

/**
 * Subscription sign-in *state* for the embedded runtime. The actual login is brain-driven (the brain's
 * loopback OAuth via BrainClient, or a pasted `setup-token`); this only reports whether credentials
 * exist — under `$HOME/.claude` (the brain runs with the same HOME) or as a pasted token in Prefs.
 */
class ClaudeAuth(private val ctx: Context) {
    private val home get() = EmbeddedRuntime.homeDir(ctx)

    /** True once a subscription token is pasted, or the brain's home holds Claude credentials. */
    fun isSignedIn(): Boolean =
        dev.kris.clyde.util.Prefs.oauthToken.isNotBlank() ||
            File(home, ".claude/.credentials.json").exists() || File(home, ".claude.json").exists()
}
