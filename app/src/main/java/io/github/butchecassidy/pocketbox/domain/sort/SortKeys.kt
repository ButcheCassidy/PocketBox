// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain.sort

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * Library sorting rules, used everywhere:
 * - case-insensitive and accent-insensitive (é = e), via [java.text.Collator] at PRIMARY strength
 * - leading "The ", "A " and "An " are ignored ("The Beatles" sorts under B)
 * - natural number order ("Track 2" before "Track 10")
 * - a sort tag (TITLESORT, ARTISTSORT, ALBUMSORT) replaces the display text when present
 *
 * [key] produces collation-key bytes. Comparing them byte by byte (as SQLite does for BLOBs) gives
 * exactly the Collator order, so the database can sort 20,000 tracks without loading them.
 */
object SortKeys {
    private val leadingArticles = listOf("the ", "a ", "an ")
    private val digitRun = Regex("\\d+")
    private val combiningMarks = Regex("\\p{Mn}+")
    private const val NUMBER_WIDTH = 12

    // Collator instances are not thread-safe; each thread gets its own.
    private val collator = ThreadLocal.withInitial {
        Collator.getInstance(Locale.ROOT).apply {
            strength = Collator.PRIMARY
            decomposition = Collator.CANONICAL_DECOMPOSITION
        }
    }

    /** The text the sort order is based on: the sort tag if present, without a leading article. */
    fun sortBase(text: String, sortTag: String? = null): String {
        val base = sortTag?.trim()?.takeIf { it.isNotEmpty() } ?: text.trim()
        val lower = base.lowercase(Locale.ROOT)
        val article = leadingArticles.firstOrNull { lower.startsWith(it) && base.length > it.length }
        return if (article != null) base.substring(article.length).trimStart() else base
    }

    /** Pads every run of digits so that numbers compare by value. Very long runs are kept as is. */
    fun naturalize(text: String): String = digitRun.replace(text) { match ->
        val digits = match.value.trimStart('0').ifEmpty { "0" }
        if (digits.length >= NUMBER_WIDTH) digits else digits.padStart(NUMBER_WIDTH, '0')
    }

    /** Collation-key bytes for storing in the database and sorting with a plain byte comparison. */
    fun key(text: String, sortTag: String? = null): ByteArray =
        collator.get()!!.getCollationKey(naturalize(sortBase(text, sortTag))).toByteArray()

    /** In-memory comparator with the same rules as [key]. */
    val comparator: Comparator<String> = Comparator { a, b ->
        val c = collator.get()!!
        c.compare(naturalize(sortBase(a)), naturalize(sortBase(b)))
    }

    /** Compares two stored keys byte by byte (unsigned), matching SQLite's BLOB ordering. */
    fun compareKeys(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return a.size - b.size
    }

    /**
     * The letter header a title files under: its first letter with accents removed, upper-cased.
     * Numbers and symbols file under "#".
     */
    fun letter(text: String, sortTag: String? = null): String {
        val base = sortBase(text, sortTag)
        val first = base.firstOrNull() ?: return "#"
        if (!first.isLetter()) return "#"
        val folded = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).replace(combiningMarks, "")
        return folded.uppercase(Locale.ROOT).ifEmpty { "#" }
    }
}
