package dev.kris.clyde.voice

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** Extracts the model bundle's single top-level directory without following archive or disk links. */
internal object SafeModelTarExtractor {
    private val drivePath = Regex("^[A-Za-z]:.*")

    @Throws(IOException::class)
    fun extract(tar: TarArchiveInputStream, modelDir: File) {
        val requestedRoot = modelDir.toPath().toAbsolutePath().normalize()
        val requestedParent = requestedRoot.parent
            ?: throw IOException("Model directory has no parent")
        val root = requestedParent.toFile().canonicalFile.toPath()
            .resolve(requestedRoot.fileName.toString())

        if (Files.isSymbolicLink(requestedRoot) || Files.isSymbolicLink(root)) {
            throw IOException("Model directory must not be a symbolic link")
        }
        ensureDirectory(root, root)

        var entry = tar.nextEntry
        while (entry != null) {
            if (entry.isLink || entry.isSymbolicLink) {
                throw IOException("Archive link entries are not supported")
            }

            val target = destination(root, modelDir.name, entry)
            when {
                entry.isDirectory -> ensureDirectory(root, target)
                entry.isFile -> writeFile(tar, root, target)
                else -> throw IOException("Unsupported tar entry type")
            }
            entry = tar.nextEntry
        }
    }

    private fun destination(
        root: java.nio.file.Path,
        archiveRoot: String,
        entry: TarArchiveEntry,
    ): java.nio.file.Path {
        val name = entry.name
        if (
            name.isEmpty() || '\u0000' in name || '\\' in name ||
            name.startsWith('/') || drivePath.matches(name)
        ) {
            throw IOException("Unsafe archive entry path")
        }

        val parts = name.split('/')
        if (parts.firstOrNull() != archiveRoot) throw IOException("Unexpected archive root")

        val relativeParts = parts.drop(1).toMutableList()
        if (relativeParts.lastOrNull() == "") {
            if (!entry.isDirectory) throw IOException("File entry has a trailing separator")
            relativeParts.removeAt(relativeParts.lastIndex)
        }
        if (relativeParts.any { it.isEmpty() || it == "." || it == ".." }) {
            throw IOException("Unsafe archive entry path")
        }
        if (relativeParts.isEmpty() && !entry.isDirectory) {
            throw IOException("Archive root must be a directory")
        }

        val target = relativeParts.fold(root) { path, part -> path.resolve(part) }.normalize()
        requireContained(root, target)
        return target
    }

    private fun ensureDirectory(root: java.nio.file.Path, directory: java.nio.file.Path) {
        requireContained(root, directory)
        if (Files.isSymbolicLink(root)) throw IOException("Model directory must not be a symbolic link")
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(root)
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("Model extraction path is not a directory")
        }

        var current = root
        for (part in root.relativize(directory)) {
            current = current.resolve(part)
            if (Files.isSymbolicLink(current)) throw IOException("Archive destination crosses a symbolic link")
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(current)
            if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw IOException("Archive destination parent is not a directory")
            }
            requireContained(root, current)
        }
    }

    private fun writeFile(tar: TarArchiveInputStream, root: java.nio.file.Path, target: java.nio.file.Path) {
        val parent = target.parent ?: throw IOException("Archive file has no parent")
        ensureDirectory(root, parent)
        if (Files.isSymbolicLink(target)) throw IOException("Archive destination is a symbolic link")
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
        ) {
            throw IOException("Archive destination is not a regular file")
        }
        requireContained(root, target)

        // Write beside the destination and rename into place so an existing hard link is never opened
        // and truncated. The temporary file is on the same filesystem as the model directory.
        val temporary = Files.createTempFile(parent, ".clyde-model-", ".tmp")
        try {
            Files.newOutputStream(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use {
                tar.copyTo(it)
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun requireContained(root: java.nio.file.Path, path: java.nio.file.Path) {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val normalizedPath = path.toAbsolutePath().normalize()
        if (!normalizedPath.startsWith(normalizedRoot) ||
            !path.toFile().canonicalFile.toPath().startsWith(normalizedRoot)
        ) {
            throw IOException("Archive entry escapes the model directory")
        }
    }
}
