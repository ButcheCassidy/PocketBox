// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain.lyrics

import io.github.butchecassidy.pocketbox.domain.model.LyricLine
import io.github.butchecassidy.pocketbox.domain.model.Lyrics

/**
 * Parses LRC lyrics (sidecar `.lrc` files or LRC text stored in tags).
 *
 * Supports several time stamps per line (`[00:12.30][01:40.00]Chorus`), 1–3 digit fractions after a
 * dot or colon (`[00:12:30]` is 12.3 s), the `[offset:±ms]` tag, and "enhanced" word time stamps (`<00:12.50>`), which are removed.
 * Text without any time stamps is returned as [Lyrics.Unsynced]. Malformed lines are skipped.
 */
object LrcParser {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""^\[offset:\s*([+-]?\d{1,7})\s*]$""", RegexOption.IGNORE_CASE)
    private val metadataTag = Regex("""^\[[a-zA-Z#]+:[^\]]*]$""")
    private val wordTag = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")
    private const val MAX_LINES = 5_000

    fun parse(text: String): Lyrics? {
        val clean = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        if (clean.isBlank()) return null
        var offsetMs = 0L
        val lines = ArrayList<LyricLine>()
        val plain = StringBuilder()
        var sawTimeTag = false

        for (raw in clean.lineSequence().take(MAX_LINES)) {
            val line = raw.trim()
            offsetTag.find(line)?.let { offsetMs = it.groupValues[1].toLong(); continue }
            val stamps = leadingStamps(line)
            if (stamps.isEmpty()) {
                if (!metadataTag.matches(line)) plain.appendLine(raw.trimEnd())
                continue
            }
            sawTimeTag = true
            val lyric = line.substring(stamps.last().range.last + 1).replace(wordTag, "").trim()
            stamps.forEach { lines += LyricLine(timeMs = it.timeMs, text = lyric) }
        }

        if (!sawTimeTag) {
            val unsynced = plain.toString().trim()
            return if (unsynced.isEmpty()) null else Lyrics.Unsynced(unsynced)
        }
        // A positive LRC offset means the lyrics should appear earlier.
        val adjusted = lines
            .map { it.copy(timeMs = (it.timeMs - offsetMs).coerceAtLeast(0)) }
            .sortedBy { it.timeMs }
        return if (adjusted.isEmpty()) null else Lyrics.Synced(adjusted)
    }

    private class Stamp(val timeMs: Long, val range: IntRange)

    /** Reads consecutive time tags at the start of a line. */
    private fun leadingStamps(line: String): List<Stamp> {
        val stamps = ArrayList<Stamp>(2)
        var index = 0
        while (index < line.length && line[index] == '[') {
            val match = timeTag.matchAt(line, index) ?: break
            stamps += Stamp(toMillis(match.groupValues), match.range)
            index = match.range.last + 1
        }
        return stamps
    }

    private fun toMillis(groups: List<String>): Long {
        val minutes = groups[1].toLong()
        val seconds = groups[2].toLong()
        val fraction = groups[3]
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.toLong()
        }
        return (minutes * 60 + seconds) * 1000 + fractionMs
    }
}
