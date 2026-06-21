package dev.kris.clyde.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
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
        if (!EmbeddedRuntime.isInstalled(ctx)) { Log.w(tag, "runtime not installed; cannot start brain"); return }
        if (!brainEntry.exists()) { Log.w(tag, "brain entry missing: ${brainEntry.absolutePath}"); return }
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
                if (stopped) break
                Log.w(tag, "brain exited (code=$code); restarting in ${backoffMs}ms")
                runCatching { Thread.sleep(backoffMs) }
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    private fun launchOnce(clydeKey: String): Boolean = try {
        val node = EmbeddedRuntime.nodeBin(ctx)
        val prefixPath = prefix.absolutePath
        val pb = ProcessBuilder(node.absolutePath, brainEntry.absolutePath)
        pb.directory(brainEntry.parentFile)
        pb.redirectErrorStream(true)
        // The brain's stdout/stderr can echo SMS/contacts/screen content the agent handled. Persist it
        // ONLY in a debuggable build (capped); in release, discard so nothing sensitive lands on disk.
        val debuggable = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            if (logFile.length() > 1_000_000L) logFile.delete()
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        } else {
            pb.redirectOutput(File("/dev/null"))
        }
        pb.environment().apply {
            put("PREFIX", prefixPath)
            put("HOME", home.absolutePath)
            put("TMPDIR", File(prefix, "tmp").apply { mkdirs() }.absolutePath)
            put("PATH", "$prefixPath/bin:" + (get("PATH") ?: ""))
            put("LD_LIBRARY_PATH", "$prefixPath/lib")
            put("LD_PRELOAD", EmbeddedRuntime.termuxExecLib(ctx).absolutePath) // W^X workaround
            put("LANG", "en_US.UTF-8")
            put("CLYDE_KEY", clydeKey)
            put("CLAUDE_CLI_PATH", "$prefixPath/lib/node_modules/@anthropic-ai/claude-code/cli.js")
            put("BRAIN_HOST", "127.0.0.1")
            put("BRAIN_PORT", "8765")
            remove("ANTHROPIC_API_KEY") // subscription only — never bill per token
        }
        proc = pb.start()
        Log.i(tag, "brain started (${node.name} ${brainEntry.name})")
        true
    } catch (e: Exception) {
        Log.e(tag, "failed to launch brain", e)
        false
    }

    @Synchronized
    fun stop() {
        stopped = true
        runCatching { proc?.destroy() }
        proc = null
        runCatching { logFile.delete() } // don't leave captured brain output on disk after a session
    }
}
