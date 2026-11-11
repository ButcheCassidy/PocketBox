// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain.lyrics

import io.github.butchecassidy.pocketbox.domain.model.LyricLine
import io.github.butchecassidy.pocketbox.domain.model.LyricWord
import io.github.butchecassidy.pocketbox.domain.model.Lyrics

/**
 * Parses LRC lyrics (sidecar `.lrc` files or LRC text stored in tags).
 *
 * Supports several time stamps per line (`[00:12.30][01:40.00]Chorus`), 1–3 digit fractions after a
 * dot or colon (`[00:12:30]` is 12.3 s), the `[offset:±ms]` tag, and "enhanced" word time stamps (`<00:12.50>`),
 * which become [LyricWord]s so the words light up as they are sung.
 * Text without any time stamps is returned as [Lyrics.Unsynced]. Malformed lines are skipped.
 */
object LrcParser {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""^\[offset:\s*([+-]?\d{1,7})\s*]$""", RegexOption.IGNORE_CASE)
    private val metadataTag = Regex("""^\[[a-zA-Z#]+:[^\]]*]$""")
    private val wordTag = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
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
            val body = line.substring(stamps.last().range.last + 1)
            val lyric = body.replace(wordTag, "").trim()
            val words = words(body, stamps.first().timeMs)
            // Word stamps are absolute; a line repeated at another time keeps its rhythm, shifted.
            stamps.forEach { stamp ->
                val shift = stamp.timeMs - stamps.first().timeMs
                lines += LyricLine(stamp.timeMs, lyric, words.map { it.copy(timeMs = it.timeMs + shift) })
            }
        }

        if (!sawTimeTag) {
            val unsynced = plain.toString().trim()
            return if (unsynced.isEmpty()) null else Lyrics.Unsynced(unsynced)
        }
        // A positive LRC offset means the lyrics should appear earlier.
        val adjusted = lines
            .map { line ->
                line.copy(
                    timeMs = (line.timeMs - offsetMs).coerceAtLeast(0),
                    words = line.words.map { it.copy(timeMs = (it.timeMs - offsetMs).coerceAtLeast(0)) },
                )
            }
            .sortedBy { it.timeMs }
        return if (adjusted.isEmpty()) null else Lyrics.Synced(adjusted)
    }

    /**
     * Splits a line body at its word stamps. Text before the first stamp starts with the line; a stamp
     * followed by no text only marks where the last word ends, so it is dropped. No stamps: no words.
     */
    private fun words(body: String, lineTimeMs: Long): List<LyricWord> {
        val matches = wordTag.findAll(body).toList()
        if (matches.isEmpty()) return emptyList()
        val words = ArrayList<LyricWord>(matches.size + 1)
        val lead = body.substring(0, matches.first().range.first)
        if (lead.isNotBlank()) words += LyricWord(lineTimeMs, lead)
        matches.forEachIndexed { index, match ->
            val end = matches.getOrNull(index + 1)?.range?.first ?: body.length
            val text = body.substring(match.range.last + 1, end)
            if (text.isNotBlank()) words += LyricWord(toMillis(match.groupValues), text)
        }
        if (words.isEmpty()) return emptyList()
        words[0] = words[0].copy(text = words[0].text.trimStart())
        words[words.lastIndex] = words.last().copy(text = words.last().text.trimEnd())
        return words
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
