// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain.model

/** Lyrics for one track, from its own tags or a sidecar .lrc file. Never fetched from anywhere. */
sealed interface Lyrics {
    /** Time-stamped lines that can scroll with playback and be tapped to seek. */
    data class Synced(val lines: List<LyricLine>) : Lyrics {
        /** Index of the line being sung at [positionMs], or -1 before the first line. */
        fun indexAt(positionMs: Long): Int {
            var low = 0
            var high = lines.lastIndex
            var result = -1
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (lines[mid].timeMs <= positionMs) {
                    result = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return result
        }
    }

    /** Plain lyrics without timing. */
    data class Unsynced(val text: String) : Lyrics
}

/** One line; [words] is filled only for "enhanced" LRC, where every word carries its own time. */
data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList()) {
    /** How many words, from the first, have started at [positionMs] (a mistimed word never skips ahead). */
    fun wordsSungAt(positionMs: Long): Int = words.indexOfFirst { it.timeMs > positionMs }.let { if (it < 0) words.size else it }
}

/** A word (with its trailing space) and when it starts. */
data class LyricWord(val timeMs: Long, val text: String)
