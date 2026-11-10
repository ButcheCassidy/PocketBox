// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain.search

import java.text.Normalizer
import java.util.Locale

/** How well a song's title matches the query; lower is better. */
enum class MatchTier {
    TITLE_STARTS_WITH,
    TITLE_WORD_STARTS_WITH,
    TITLE_CONTAINS,
    OTHER_FIELD,
}

/**
 * Search-as-you-type text rules: case-insensitive, accent-insensitive (é = e), punctuation ignored,
 * and multi-word queries match words in any order ("go let" finds "Let Go").
 */
object SearchText {
    private const val MAX_TOKENS = 8
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")

    /** Folds one character the same way everywhere, keeping string positions aligned. */
    private fun fold(c: Char): Char {
        val decomposed = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
        return decomposed.first().lowercaseChar()
    }

    /** Folded copy of [text], same length, so a match position is also a position in [text]. */
    fun folded(text: String): String {
        val out = CharArray(text.length)
        for (i in text.indices) out[i] = fold(text[i])
        return String(out)
    }

    /** The query's words, folded, without punctuation. */
    fun tokens(query: String): List<String> =
        folded(query).split(nonWord).filter { it.isNotEmpty() }.take(MAX_TOKENS)

    /** An FTS4 prefix query ("let go" → `let* go*`), or null when there's nothing to search for. */
    fun ftsQuery(tokens: List<String>): String? =
        tokens.takeIf { it.isNotEmpty() }?.joinToString(" ") { "$it*" }

    /** Ranks a title against the query tokens (all tokens must appear for the title tiers). */
    fun tier(title: String, tokens: List<String>): MatchTier {
        if (tokens.isEmpty()) return MatchTier.OTHER_FIELD
        val foldedTitle = folded(title)
        val joined = tokens.joinToString(" ")
        val words = foldedTitle.split(nonWord).filter { it.isNotEmpty() }
        val titleSquashed = words.joinToString(" ")
        return when {
            titleSquashed.startsWith(joined) -> MatchTier.TITLE_STARTS_WITH
            tokens.all { token -> words.any { it.startsWith(token) } } -> MatchTier.TITLE_WORD_STARTS_WITH
            tokens.all { foldedTitle.contains(it) } -> MatchTier.TITLE_CONTAINS
            else -> MatchTier.OTHER_FIELD
        }
    }

    /** Character ranges in [text] to show in bold: every place a query word appears. */
    fun highlights(text: String, tokens: List<String>): List<IntRange> {
        if (tokens.isEmpty()) return emptyList()
        val foldedText = folded(text)
        val ranges = ArrayList<IntRange>()
        for (token in tokens) {
            var from = 0
            while (true) {
                val at = foldedText.indexOf(token, from)
                if (at < 0) break
                ranges += at until at + token.length
                from = at + token.length
            }
        }
        return merge(ranges)
    }

    private fun merge(ranges: List<IntRange>): List<IntRange> {
        val sorted = ranges.sortedBy { it.first }
        val out = ArrayList<IntRange>()
        for (range in sorted) {
            val last = out.lastOrNull()
            if (last != null && range.first <= last.last + 1) {
                out[out.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                out += range
            }
        }
        return out
    }

    /** Lower-cased, accent-free words of a name, for typo-tolerant matching. */
    fun words(text: String): List<String> = folded(text).lowercase(Locale.ROOT).split(nonWord).filter { it.isNotEmpty() }
}

/**
 * Typo forgiveness: Damerau-Levenshtein distance (optimal string alignment), so one swapped pair of
 * letters ("Beatels") counts as a single edit. Stops early once the limit is exceeded.
 */
object Fuzzy {
    /** Allowed edits: none for 1–3 letters, 1 for 4–7, 2 for 8 or more. */
    fun allowedDistance(queryLength: Int): Int = when {
        queryLength >= 8 -> 2
        queryLength >= 4 -> 1
        else -> 0
    }

    fun distance(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var previous2 = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = minOf(value, previous2[j - 2] + 1)
                }
                current[j] = value
                rowMin = minOf(rowMin, value)
            }
            if (rowMin > limit) return limit + 1
            val recycled = previous2
            previous2 = previous
            previous = current
            current = recycled
        }
        return previous[b.length]
    }

    /**
     * True when [name] is a close (but not exact) match for [query]: the whole name, the name
     * without its leading article, or any single word of it, within the allowed distance.
     */
    fun isCloseMatch(query: String, name: String): Boolean {
        val folded = SearchText.words(query).joinToString(" ")
        val limit = allowedDistance(folded.length)
        if (limit == 0) return false
        val words = SearchText.words(name)
        val candidates = buildList {
            add(words.joinToString(" "))
            if (words.size > 1) add(words.drop(1).joinToString(" "))
            addAll(words)
        }
        return candidates.any { candidate -> candidate != folded && distance(folded, candidate, limit) <= limit }
    }
}
