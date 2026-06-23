package dev.kris.clyde.runtime

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.OutputStreamWriter
import kotlin.concurrent.thread

/**
 * Drives the OpenAI Codex CLI's subscription sign-in inside the embedded runtime — the Codex analog of
 * [ClaudeAuth]. Runs `codex login` in `$HOME` = files/home so the creds it writes (under `$HOME/.codex`)
 * are the SAME ones the brain's Codex backend uses. ChatGPT OAuth happens in the browser; Clyde never
 * sees the password/token. Subscription only — refuses to start if OPENAI_API_KEY is present.
 *
 * General interactive driver (streams stdout, surfaces the first OAuth URL for the app to open in the
 * real default browser, and can feed a pasted device code to stdin) so it's robust to the CLI prompts.
 *
 * NOTE: the `codex` binary is native (arm64-musl) and ships in the embedded bootstrap; until that lands
 * `cli` won't exist and start() reports failure cleanly.
 */
class CodexAuth(private val ctx: Context) {
    private val tag = "ClydeRuntime"
    @Volatile private var proc: Process? = null
    @Volatile private var stdin: OutputStreamWriter? = null

    private val prefix get() = EmbeddedRuntime.prefixDir(ctx)
    private val home get() = EmbeddedRuntime.homeDir(ctx)
    private val cli get() = File(prefix, "bin/codex")
    private val urlRegex = Regex("""https?://[^\s'"]+""")

    fun isRunning(): Boolean = proc?.isAlive == true

    /**
     * Start `codex login`. [onLine] gets each output line (the device code prints here); [onUrl] fires
     * for the first OAuth URL (open it in a browser); [onResult] fires on exit (true = exit 0).
     */
    @Synchronized
    fun start(onLine: (String) -> Unit, onUrl: (String) -> Unit, onResult: (Boolean) -> Unit) {
        if (isRunning()) return
        if (!cli.exists()) { Log.w(tag, "codex binary missing: ${cli.absolutePath}"); onResult(false); return }
        try {
            val prefixPath = prefix.absolutePath
            // --device-auth: device-code flow (no localhost callback), the right path on a phone.
            val pb = ProcessBuilder(cli.absolutePath, "login", "--device-auth")
            pb.directory(home.also { it.mkdirs() })
            pb.redirectErrorStream(true)
            pb.environment().apply {
                put("PREFIX", prefixPath)
                put("HOME", home.absolutePath)
                put("TMPDIR", File(prefix, "tmp").apply { mkdirs() }.absolutePath)
                put("PATH", "$prefixPath/bin:" + (get("PATH") ?: ""))
                put("LD_LIBRARY_PATH", "$prefixPath/lib")
                put("LANG", "en_US.UTF-8")
                // Subscription only — never let an API key (or off-sub base URL) hijack billing.
                remove("OPENAI_API_KEY")
                remove("CODEX_API_KEY")
                remove("OPENAI_BASE_URL")
            }
            val p = pb.start()
            proc = p
            stdin = OutputStreamWriter(p.outputStream)
            var urlSent = false
            thread(name = "clyde-codex-out", isDaemon = true) {
                p.inputStream.bufferedReader().use { r: BufferedReader ->
                    r.forEachLine { line ->
                        onLine(line)
                        if (!urlSent) urlRegex.find(line)?.let { urlSent = true; onUrl(it.value) }
                    }
                }
            }
            thread(name = "clyde-codex-wait", isDaemon = true) {
                val ok = runCatching { p.waitFor() == 0 }.getOrDefault(false)
                proc = null
                onResult(ok)
            }
            Log.i(tag, "codex login started")
        } catch (e: Exception) {
            Log.e(tag, "codex login failed to start", e)
            onResult(false)
        }
    }

    /** Feed a pasted device code (or any input) to the login process's stdin. */
    fun submit(text: String) {
        runCatching { stdin?.apply { write(text); write("\n"); flush() } }
    }

    /** True once Codex credentials exist in the brain's home (i.e. sign-in completed). */
    fun isSignedIn(): Boolean =
        File(home, ".codex/auth.json").exists()

    @Synchronized
    fun cancel() {
        runCatching { proc?.destroy() }
        proc = null
    }
}
