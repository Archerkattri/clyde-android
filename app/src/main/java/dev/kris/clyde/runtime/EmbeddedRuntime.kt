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
    fun nodeBin(ctx: Context): File = File(prefixDir(ctx), "bin/node")
    // termux-exec 2.x ships its LD_PRELOAD shim as libtermux-exec-ld-preload.so (the 1.x
    // libtermux-exec.so name is gone). This is the canonical lib Termux itself preloads.
    fun termuxExecLib(ctx: Context): File = File(prefixDir(ctx), "lib/libtermux-exec-ld-preload.so")

    /** Is the runtime already extracted and runnable? */
    fun isInstalled(ctx: Context): Boolean = nodeBin(ctx).exists()

    /** Does THIS build actually bundle a runtime for this device's ABI? (arm64 only.) */
    fun isBundled(ctx: Context): Boolean =
        runCatching { ctx.assets.open(ASSET_ARM64).close(); true }.getOrDefault(false)

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

            ctx.assets.open(ASSET_ARM64).use { raw ->
                ZipInputStream(raw.buffered()).use { zin ->
                    var entry = zin.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        when {
                            name == "SYMLINKS.txt" -> zin.bufferedReader().forEachLine { line ->
                                val i = line.indexOf('←') // '←'
                                if (i > 0) symlinks.add(line.substring(0, i) to line.substring(i + 1))
                            }
                            entry.isDirectory -> safeChild(name).mkdirs()
                            else -> {
                                val out = safeChild(name)
                                out.parentFile?.mkdirs()
                                out.outputStream().use { zin.copyTo(it) }
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

            for ((target, linkPath) in symlinks) {
                val link = safeChild(linkPath)
                link.parentFile?.mkdirs()
                runCatching { link.delete(); Os.symlink(target, link.absolutePath) }.onFailure { Log.w(TAG, "symlink failed: $linkPath ← $target", it) }
            }

            prefix.deleteRecursively()
            check(staging.renameTo(prefix)) { "staging → prefix rename failed" }
            homeDir(ctx).mkdirs()
            versionFile.writeText(version)
            Log.i(TAG, "embedded runtime installed ($version)")
            true
        }.getOrElse {
            Log.e(TAG, "embedded runtime install failed", it)
            false
        }
    }
}
