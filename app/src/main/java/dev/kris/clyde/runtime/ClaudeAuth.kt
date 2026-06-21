package dev.kris.clyde.runtime

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.OutputStreamWriter
import kotlin.concurrent.thread

/**
 * Drives a one-time subscription sign-in inside the embedded runtime — clean-room, plain
 * ProcessBuilder. Runs the JS claude-code CLI's login in `$HOME` = files/home, so the credentials it
 * writes (under `$HOME/.claude`) are the SAME ones the brain uses (BrainRunner runs with the same
 * HOME). Nothing here ever sees the password/token — Claude's own OAuth handles it in the browser.
 *
 * It's a general interactive driver (streams stdout, surfaces any https URL for the app to open in a
 * browser tab, and can feed a pasted code to stdin) so it's robust to the exact CLI prompts.
 */
class ClaudeAuth(private val ctx: Context) {
    private val tag = "ClydeRuntime"
    @Volatile private var proc: Process? = null
    @Volatile private var stdin: OutputStreamWriter? = null

    private val prefix get() = EmbeddedRuntime.prefixDir(ctx)
    private val home get() = EmbeddedRuntime.homeDir(ctx)
    private val cli get() = File(prefix, "lib/node_modules/@anthropic-ai/claude-code/cli.js")
    private val urlRegex = Regex("""https?://[^\s'"]+""")

    fun isRunning(): Boolean = proc?.isAlive == true

    /**
     * Start the login. [onLine] gets each output line; [onUrl] fires for the first OAuth URL seen
     * (open it in a browser); [onResult] fires when the process exits (true = exit 0).
     */
    @Synchronized
    fun start(onLine: (String) -> Unit, onUrl: (String) -> Unit, onResult: (Boolean) -> Unit) {
        if (isRunning()) return
        if (!cli.exists()) { Log.w(tag, "claude CLI missing: ${cli.absolutePath}"); onResult(false); return }
        try {
            val node = EmbeddedRuntime.nodeBin(ctx)
            val prefixPath = prefix.absolutePath
            // `setup-token` / `login` performs the subscription OAuth and stores creds under $HOME/.claude.
            val pb = ProcessBuilder(node.absolutePath, cli.absolutePath, "setup-token")
            pb.directory(home.also { it.mkdirs() })
            pb.redirectErrorStream(true)
            pb.environment().apply {
                put("PREFIX", prefixPath)
                put("HOME", home.absolutePath)
                put("TMPDIR", File(prefix, "tmp").apply { mkdirs() }.absolutePath)
                put("PATH", "$prefixPath/bin:" + (get("PATH") ?: ""))
                put("LD_LIBRARY_PATH", "$prefixPath/lib")
                put("LD_PRELOAD", EmbeddedRuntime.termuxExecLib(ctx).absolutePath)
                put("LANG", "en_US.UTF-8")
                remove("ANTHROPIC_API_KEY")
            }
            val p = pb.start()
            proc = p
            stdin = OutputStreamWriter(p.outputStream)
            var urlSent = false
            thread(name = "clyde-auth-out", isDaemon = true) {
                p.inputStream.bufferedReader().use { r: BufferedReader ->
                    r.forEachLine { line ->
                        onLine(line)
                        if (!urlSent) urlRegex.find(line)?.let { urlSent = true; onUrl(it.value) }
                    }
                }
            }
            thread(name = "clyde-auth-wait", isDaemon = true) {
                val ok = runCatching { p.waitFor() == 0 }.getOrDefault(false)
                proc = null
                onResult(ok)
            }
            Log.i(tag, "claude login started")
        } catch (e: Exception) {
            Log.e(tag, "claude login failed to start", e)
            onResult(false)
        }
    }

    /** Feed a pasted code (or any input) to the login process's stdin. */
    fun submit(text: String) {
        runCatching { stdin?.apply { write(text); write("\n"); flush() } }
    }

    /** True once the brain's home holds Claude credentials (i.e. sign-in completed). */
    fun isSignedIn(): Boolean =
        File(home, ".claude/.credentials.json").exists() || File(home, ".claude.json").exists()

    @Synchronized
    fun cancel() {
        runCatching { proc?.destroy() }
        proc = null
    }
}
