package dev.kris.clyde.runtime

import android.content.Context
import android.system.Os
import android.util.Log
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Clean-room extractor for Clyde's purpose-built Linux runtime.
 *
 * The runtime is a Termux-style bootstrap BUILT FOR THE dev.kris.clyde PREFIX (see /bootstrap),
 * containing only what the brain needs (Node + the JS claude-code CLI + the brain itself). This
 * class is a from-scratch reimplementation of the documented bootstrap format — a flat zip of files
 * plus a `SYMLINKS.txt` of `target←linkpath` lines — and uses NO Termux (GPLv3) code, so Clyde's own
 * code stays license-flexible. Only the bundled binaries carry their own licenses (see NOTICE.md).
 *
 * Everything lives under the app's private files dir, so it's removed cleanly on uninstall and never
 * collides with a real Termux install:
 *   $PREFIX = files/usr      $HOME = files/home
 */
object EmbeddedRuntime {
    private const val TAG = "ClydeRuntime"

    /** Per-ABI bootstrap baked into the APK. Only arm64 is shipped (see /bootstrap/README.md). */
    const val ASSET_ARM64 = "bootstrap-aarch64.zip"
    private const val VERSION_FILE = ".clyde-bootstrap-version"

    fun prefixDir(ctx: Context): File = File(ctx.filesDir, "usr")
    fun homeDir(ctx: Context): File = File(ctx.filesDir, "home")
    // node runs from the app's nativeLibraryDir, NOT app storage: Android's W^X blocks exec'ing a
    // binary out of files/ at targetSdk 29+ (error=13). nativeLibraryDir is the one exec-allowed spot,
    // so node + its native .so closure ship via jniLibs (as libnode.so etc.; see app/src/main/jniLibs).
    fun nativeLibDir(ctx: Context): File = File(ctx.applicationInfo.nativeLibraryDir)
    fun nodeBin(ctx: Context): File = File(nativeLibDir(ctx), "libnode.so")
    /** The brain entry (extracted from the asset into app storage; node READS it — reading is fine). */
    fun brainEntry(ctx: Context): File = File(prefixDir(ctx), "opt/clyde-brain/server.js")

    /** Ready = the asset (brain + CLI + node_modules + libs) is extracted. node itself rides in the APK. */
    fun isInstalled(ctx: Context): Boolean = brainEntry(ctx).exists()

    /** Does THIS build actually bundle a runtime for this device's ABI? (arm64 only.) */
    fun isBundled(ctx: Context): Boolean =
        runCatching { ctx.assets.open(ASSET_ARM64).close(); true }.getOrDefault(false)

    // The extracted runtime is ~200 MB; require headroom so we fail fast + clearly when truly low.
    private const val NEEDED_BYTES = 280L * 1024 * 1024
    private const val EST_TOTAL_BYTES = 214L * 1024 * 1024 // our build extracts to ~214 MB
    fun freeMb(ctx: Context): Long = ctx.filesDir.usableSpace / (1024 * 1024)
    fun lowStorage(ctx: Context): Boolean = ctx.filesDir.usableSpace < NEEDED_BYTES

    /** Live unpack status the setup screen polls. [etaSeconds] < 0 = not yet estimable. */
    data class Progress(val phase: String, val bytes: Long, val totalBytes: Long, val files: Int, val etaSeconds: Int)
    @Volatile var progress: Progress? = null; private set
    @Volatile var lastError: String? = null; private set

    /**
     * Extract the bootstrap if missing or if [version] changed. Returns true if the runtime is ready.
     * Safe to call repeatedly; the extraction is atomic (staging dir → rename).
     */
    @Synchronized
    fun ensureInstalled(ctx: Context, version: String): Boolean {
        if (!isBundled(ctx)) {
            Log.w(TAG, "no $ASSET_ARM64 bundled in this build (arm64 device required)")
            return false
        }
        val prefix = prefixDir(ctx)
        val versionFile = File(ctx.filesDir, VERSION_FILE)
        if (isInstalled(ctx) && runCatching { versionFile.readText() }.getOrNull() == version) return true

        // Fail fast + loud when genuinely out of space, instead of dying mid-extract with a vague error.
        if (lowStorage(ctx)) {
            Log.e(TAG, "low storage: ${freeMb(ctx)} MB free, need ~${NEEDED_BYTES / (1024 * 1024)} MB to unpack the runtime")
            return false
        }

        lastError = null
        val startMs = System.currentTimeMillis()
        var bytes = 0L
        var files = 0
        var phase = "Starting"
        fun report() {
            val el = System.currentTimeMillis() - startMs
            val eta = if (bytes > 0L && el > 1500L) (((EST_TOTAL_BYTES - bytes).coerceAtLeast(0L) * el) / bytes / 1000L).toInt() else -1
            progress = Progress(phase, bytes, EST_TOTAL_BYTES, files, eta)
        }
        return runCatching {
            val staging = File(ctx.filesDir, "usr-staging").apply { deleteRecursively(); mkdirs() }
            val stagingCanon = staging.canonicalPath
            // zip-slip guard: every entry/symlink path MUST resolve inside the staging dir, so a
            // crafted "../" name can never write outside the app's private runtime tree.
            fun safeChild(name: String): File {
                val f = File(staging, name)
                val canon = f.canonicalPath
                require(canon == stagingCanon || canon.startsWith(stagingCanon + File.separator)) { "unsafe path in bootstrap: $name" }
                return f
            }
            val symlinks = ArrayList<Pair<String, String>>() // target, linkPath

            phase = "Unpacking files"; report()
            ctx.assets.open(ASSET_ARM64).use { raw ->
                ZipInputStream(raw.buffered()).use { zin ->
                    var entry = zin.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        when {
                            name == "SYMLINKS.txt" -> {
                                // Read THIS entry's bytes WITHOUT closing the stream. ZipInputStream.read
                                // returns -1 at the entry's end, so readBytes() gets exactly SYMLINKS.txt;
                                // bufferedReader().forEachLine would close `zin` and the next read would
                                // throw "stream closed", aborting the whole unpack.
                                val txt = zin.readBytes().toString(Charsets.UTF_8)
                                for (raw in txt.split('\n')) {
                                    val line = raw.trimEnd('\r')
                                    val i = line.indexOf('←') // '←'
                                    if (i > 0) symlinks.add(line.substring(0, i) to line.substring(i + 1))
                                }
                            }
                            entry.isDirectory -> safeChild(name).mkdirs()
                            else -> {
                                val out = safeChild(name)
                                out.parentFile?.mkdirs()
                                out.outputStream().use { bytes += zin.copyTo(it) }
                                files++
                                if (files and 0x1F == 0) report() // every 32 files
                                // executables in the bootstrap need 0700
                                if (name.startsWith("bin/") || name.startsWith("libexec/") || name == "lib/apt/apt-helper") {
                                    runCatching { Os.chmod(out.absolutePath, 448) }.onFailure { Log.w(TAG, "chmod failed: $name", it) } // 0o700
                                }
                            }
                        }
                        zin.closeEntry()
                        entry = zin.nextEntry
                    }
                }
            }

            phase = "Linking commands"; report()
            var li = 0
            for ((target, linkPath) in symlinks) {
                val link = safeChild(linkPath)
                link.parentFile?.mkdirs()
                runCatching { link.delete(); Os.symlink(target, link.absolutePath) }.onFailure { Log.w(TAG, "symlink failed: $linkPath ← $target", it) }
                if (++li and 0xFF == 0) report()
            }

            phase = "Finishing"; report()
            prefix.deleteRecursively()
            // Os.rename is a direct rename(2) syscall — more reliable than File.renameTo (which fails
            // opaquely on Android) and throws an ErrnoException with the real reason if it does fail.
            Os.rename(staging.absolutePath, prefix.absolutePath)
            homeDir(ctx).mkdirs()
            versionFile.writeText(version)
            bytes = EST_TOTAL_BYTES; phase = "Ready"; report()
            Log.i(TAG, "embedded runtime installed ($version)")
            true
        }.getOrElse {
            lastError = "$phase failed: ${it.javaClass.simpleName}: ${it.message ?: ""}".take(220)
            progress = null
            Log.e(TAG, "embedded runtime install failed during '$phase'", it)
            false
        }
    }
}
