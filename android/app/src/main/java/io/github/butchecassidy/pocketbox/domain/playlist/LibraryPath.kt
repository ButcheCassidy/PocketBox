// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain.playlist

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Safe path handling for playlist files.
 *
 * Paths are expressed the way Android's storage provider names documents: `volume:relative/path`,
 * for example `primary:Music/Rock/song.flac`. An entry is only ever *matched* against songs already in
 * the library (which all live inside folders the user granted), and [resolve] refuses anything that
 * escapes its volume with `..`, uses a network or app scheme, or contains control characters.
 */
object LibraryPath {
    private const val MAX_ENTRY_LENGTH = 4_096
    private const val PRIMARY = "primary"
    private val emulatedRoot = Regex("^/storage/emulated/\\d+(/|$)")
    private val sdcardRoot = Regex("^/sdcard(/|$)")
    private val volumeRoot = Regex("^/(?:storage|mnt/media_rw)/([0-9A-Fa-f]{4}-[0-9A-Fa-f]{4})(/|$)")
    private val windowsDrive = Regex("^[A-Za-z]:/")
    private val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

    /**
     * Turns one playlist entry into a `volume:path` document path.
     *
     * @param playlistDir the folder the playlist file lives in, as `volume:path`, or null if unknown
     *   (relative entries then can't be resolved).
     * @return the normalised path, or null if the entry is unsafe or can't be mapped.
     */
    fun resolve(entry: String, playlistDir: String?): String? {
        var path = entry.trim()
        if (path.isEmpty() || path.length > MAX_ENTRY_LENGTH || path.any { it < ' ' }) return null
        if (scheme.containsMatchIn(path)) {
            if (!path.startsWith("file://", ignoreCase = true)) return null
            path = runCatching { URLDecoder.decode(path.substring("file://".length), StandardCharsets.UTF_8) }
                .getOrNull() ?: return null
            if (path.any { it < ' ' }) return null
        }
        path = path.replace('\\', '/')
        if (windowsDrive.containsMatchIn(path)) return null

        return if (path.startsWith("/")) {
            absoluteToDocumentPath(path)
        } else {
            val base = playlistDir?.let(::split) ?: return null
            join(base.first, base.second + "/" + path)
        }
    }

    /** `/storage/emulated/0/Music/a.mp3` → `primary:Music/a.mp3`; null for paths outside shared storage. */
    fun absoluteToDocumentPath(absolute: String): String? {
        emulatedRoot.find(absolute)?.let { return join(PRIMARY, absolute.substring(it.range.last + 1)) }
        sdcardRoot.find(absolute)?.let { return join(PRIMARY, absolute.substring(it.range.last + 1)) }
        volumeRoot.find(absolute)?.let { return join(it.groupValues[1], absolute.substring(it.range.last + 1)) }
        return null
    }

    /** `primary:Music/a.mp3` → `/storage/emulated/0/Music/a.mp3`, for exporting playlists. */
    fun toAbsolute(documentPath: String): String? {
        val (volume, rel) = split(documentPath) ?: return null
        val root = if (volume == PRIMARY) "/storage/emulated/0" else "/storage/$volume"
        return if (rel.isEmpty()) root else "$root/$rel"
    }

    /** Relative path from folder [fromDir] to [to] (same volume only), e.g. `../Rock/a.mp3`. */
    fun relativize(fromDir: String, to: String): String? {
        val (fromVolume, fromRel) = split(fromDir) ?: return null
        val (toVolume, toRel) = split(to) ?: return null
        if (fromVolume != toVolume) return null
        val fromParts = fromRel.split('/').filter { it.isNotEmpty() }
        val toParts = toRel.split('/').filter { it.isNotEmpty() }
        var common = 0
        while (common < fromParts.size && common < toParts.size - 1 && fromParts[common] == toParts[common]) common++
        val ups = List(fromParts.size - common) { ".." }
        return (ups + toParts.drop(common)).joinToString("/")
    }

    /** True when [path] is [root] itself or inside it. */
    fun isInside(path: String, root: String): Boolean {
        val (pathVolume, pathRel) = split(path) ?: return false
        val (rootVolume, rootRel) = split(root) ?: return false
        if (pathVolume != rootVolume) return false
        return rootRel.isEmpty() || pathRel == rootRel || pathRel.startsWith("$rootRel/")
    }

    /** The folder part of a document path: `primary:Music/a.mp3` → `primary:Music`. */
    fun parentOf(documentPath: String): String? {
        val (volume, rel) = split(documentPath) ?: return null
        return "$volume:" + rel.substringBeforeLast('/', missingDelimiterValue = "")
    }

    private fun split(documentPath: String): Pair<String, String>? {
        val colon = documentPath.indexOf(':')
        if (colon <= 0) return null
        return documentPath.substring(0, colon) to documentPath.substring(colon + 1).trim('/')
    }

    /** Normalises `.` and `..`; returns null if `..` would climb above the volume root. */
    private fun join(volume: String, rel: String): String? {
        val parts = ArrayDeque<String>()
        for (segment in rel.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> parts.removeLastOrNull() ?: return null
                else -> parts.addLast(segment)
            }
        }
        if (parts.isEmpty()) return null
        return "$volume:" + parts.joinToString("/")
    }
}
