package dev.kris.clyde.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import dev.kris.clyde.util.Prefs
import java.io.File
import kotlin.concurrent.thread

/**
 * Launches the brain (`node server.js`) inside the embedded runtime as a child process and keeps it
 * alive. Clean-room (plain java.lang.ProcessBuilder) — no Termux code. Headless background processes
 * never touch a PTY/JNI, so this is all that's needed.
 *
 * The W^X catch: targetSdk 36 / Android 10+ forbid exec'ing binaries from app-private storage. The
 * bootstrap ships libtermux-exec-ld-preload.so (termux-exec 2.x), which (via LD_PRELOAD) routes execve
 * through the system linker — the supported workaround. node is launched with that preload.
 */
class BrainRunner(private val ctx: Context) {
    private val tag = "ClydeRuntime"

    @Volatile private var proc: Process? = null
    @Volatile private var stopped = true
    private var supervisor: Thread? = null

    private val prefix get() = EmbeddedRuntime.prefixDir(ctx)
    private val home get() = EmbeddedRuntime.homeDir(ctx)
    // The build pipeline bundles the brain (bundled to a single file) here inside the prefix.
    private val brainEntry get() = File(prefix, "opt/clyde-brain/server.js")
    private val logFile get() = File(ctx.filesDir, "brain.log")

    fun isRunning(): Boolean = proc?.isAlive == true

    /** Start the brain (no-op if already running). Supervises + restarts on death with backoff. */
    @Synchronized
    fun start(clydeKey: String) {
        if (isRunning()) return
        if (!EmbeddedRuntime.isInstalled(ctx)) { diag = "runtime not installed (node binary missing)"; Log.w(tag, "runtime not installed; cannot start brain"); return }
        if (!brainEntry.exists()) { diag = "brain entry missing: ${brainEntry.absolutePath}"; Log.w(tag, "brain entry missing: ${brainEntry.absolutePath}"); return }
        stopped = false
        supervisor = thread(name = "clyde-brain-supervisor", isDaemon = true) {
            var backoffMs = 1000L
            var startFailures = 0
            while (!stopped) {
                if (!launchOnce(clydeKey)) {
                    // A start failure is usually transient (e.g. brief FS contention right after the
                    // first-run extraction). Back off and retry instead of killing the brain for the
                    // whole session — but give up after a few in a row so a genuinely broken binary
                    // can't spin forever.
                    if (++startFailures >= 5) { Log.e(tag, "brain failed to start ${startFailures}x; giving up this session"); break }
                    if (stopped) break
                    Log.w(tag, "brain start failed (#$startFailures); retrying in ${backoffMs}ms")
                    runCatching { Thread.sleep(backoffMs) }
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                    continue
                }
                startFailures = 0
                val code = runCatching { proc?.waitFor() }.getOrNull()
                if (!stopped && code != null) diag = "${diag}\n[brain exited code=$code]".takeLast(MAX_DIAG)
                if (stopped) break
                Log.w(tag, "brain exited (code=$code); restarting in ${backoffMs}ms")
                runCatching { Thread.sleep(backoffMs) }
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    private fun launchOnce(clydeKey: String): Boolean {
        val node = EmbeddedRuntime.nodeBin(ctx)
        val lib = EmbeddedRuntime.termuxExecLib(ctx)
        val cli = File(prefix, "lib/node_modules/@anthropic-ai/claude-code/cli.js")
        // Preflight facts — the first thing surfaced on screen if the brain won't come up. Tells us at
        // a glance whether the binary is even present/executable (the W^X exec question) before we try.
        val preflight = buildString {
            append("node: exists=${node.exists()} exec=${node.canExecute()} size=${node.length() / 1024}KB\n")
            append("preload: exists=${lib.exists()} size=${lib.length() / 1024}KB\n")
            append("cli: exists=${cli.exists()}")
        }
        diag = preflight
        return try {
            val pb = ProcessBuilder(node.absolutePath, brainEntry.absolutePath)
            pb.directory(brainEntry.parentFile)
            pb.redirectErrorStream(true) // merge stderr → stdout so the reader sees crashes
            val debuggable = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            pb.environment().apply {
                put("PREFIX", prefix.absolutePath)
                put("HOME", home.absolutePath)
                put("TMPDIR", File(prefix, "tmp").apply { mkdirs() }.absolutePath)
                put("PATH", "${prefix.absolutePath}/bin:" + (get("PATH") ?: ""))
                put("LD_LIBRARY_PATH", "${prefix.absolutePath}/lib")
                put("LD_PRELOAD", lib.absolutePath) // W^X workaround
                put("LANG", "en_US.UTF-8")
                put("CLYDE_KEY", clydeKey)
                put("CLAUDE_CLI_PATH", cli.absolutePath)
                put("BRAIN_HOST", "127.0.0.1")
                put("BRAIN_PORT", "8765")
                remove("ANTHROPIC_API_KEY") // subscription only — never bill per token
                val token = Prefs.oauthToken
                if (token.isNotBlank()) put("CLAUDE_CODE_OAUTH_TOKEN", token)
            }
            val p = pb.start()
            proc = p
            Log.i(tag, "brain started (${node.name} ${brainEntry.name})")
            // Drain the merged output so the OS pipe can't fill and block the brain. Capture a BOUNDED
            // prefix into `diag` for on-screen diagnostics, and STOP capturing the instant the brain
            // reports it's listening (or the process dies) — so query output (SMS/contacts/screen the
            // agent later handles) is never captured. In a debuggable build, also tee the full log.
            thread(name = "clyde-brain-reader", isDaemon = true) {
                val cap = StringBuilder(preflight).append("\n--- brain ---\n")
                var capturing = true
                if (debuggable && logFile.length() > 1_000_000L) runCatching { logFile.delete() }
                runCatching {
                    p.inputStream.bufferedReader().forEachLine { line ->
                        if (debuggable) runCatching { logFile.appendText(line + "\n") }
                        if (capturing) {
                            cap.append(line).append('\n')
                            diag = cap.toString().takeLast(MAX_DIAG)
                            if (line.contains("listening") || cap.length > MAX_DIAG) capturing = false
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            // The decisive signal for "node won't exec from app storage" lands HERE (IOException
            // error=13 EACCES / error=8 ENOEXEC). Surface the real reason instead of a silent /dev/null.
            diag = "$preflight\n--- launch FAILED ---\n${e.javaClass.simpleName}: ${e.message}".takeLast(MAX_DIAG)
            Log.e(tag, "failed to launch brain", e)
            false
        }
    }

    @Synchronized
    fun stop() {
        stopped = true
        runCatching { proc?.destroy() }
        proc = null
        runCatching { logFile.delete() } // don't leave captured brain output on disk after a session
    }

    companion object {
        private const val MAX_DIAG = 1600
        /** Release-safe brain bring-up diagnostics (preflight + bounded startup output / launch error).
         *  Captured only until the brain says "listening" or dies, so it never holds query content. */
        @Volatile
        var diag: String = ""
    }
}
