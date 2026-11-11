// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain.playlist

/** One line of a playlist file that points at a song. */
data class M3uEntry(val path: String, val title: String?, val durationSeconds: Int?)

/** One song to write into an exported playlist. */
data class M3uExportItem(val path: String, val artist: String, val title: String, val durationMs: Long)

/** Reads and writes `.m3u` / `.m3u8` playlists (plain and extended). Always UTF-8 when writing. */
object M3u {
    private const val MAX_ENTRIES = 50_000
    private val extInf = Regex("""^#EXTINF:\s*(-?\d+)(?:[^,]*)?,(.*)$""", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<M3uEntry> {
        val entries = ArrayList<M3uEntry>()
        var pendingTitle: String? = null
        var pendingDuration: Int? = null
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            if (entries.size >= MAX_ENTRIES) break
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#") -> extInf.find(line)?.let {
                    pendingDuration = it.groupValues[1].toIntOrNull()?.takeIf { seconds -> seconds >= 0 }
                    pendingTitle = it.groupValues[2].trim().ifEmpty { null }
                }
                else -> {
                    entries += M3uEntry(line, pendingTitle, pendingDuration)
                    pendingTitle = null
                    pendingDuration = null
                }
            }
        }
        return entries
    }

    fun write(items: List<M3uExportItem>): String = buildString {
        append("#EXTM3U\n")
        for (item in items) {
            val seconds = item.durationMs / 1000
            // A line break inside a tag or file name would otherwise start a new, unintended entry.
            val label = "${item.artist} - ${item.title}".replace('\n', ' ').replace('\r', ' ')
            append("#EXTINF:").append(seconds).append(',').append(label).append('\n')
            append(item.path.replace('\n', ' ').replace('\r', ' ')).append('\n')
        }
    }
}
