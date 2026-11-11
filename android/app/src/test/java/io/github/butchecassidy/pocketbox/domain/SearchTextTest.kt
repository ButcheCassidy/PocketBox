// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain

import io.github.butchecassidy.pocketbox.domain.search.Fuzzy
import io.github.butchecassidy.pocketbox.domain.search.MatchTier
import io.github.butchecassidy.pocketbox.domain.search.SearchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {
    private fun tier(title: String, query: String) = SearchText.tier(title, SearchText.tokens(query))

    @Test
    fun `ranking tiers follow the spec order`() {
        assertEquals(MatchTier.TITLE_STARTS_WITH, tier("Go Your Own Way", "go"))
        assertEquals(MatchTier.TITLE_WORD_STARTS_WITH, tier("Let Go", "go"))
        assertEquals(MatchTier.TITLE_CONTAINS, tier("Ego Trip", "go"))
        assertEquals(MatchTier.OTHER_FIELD, tier("Yesterday", "go"))
    }

    @Test
    fun `words match in any order, ignoring case, accents and punctuation`() {
        assertEquals(MatchTier.TITLE_WORD_STARTS_WITH, tier("Let Go", "go let"))
        assertEquals(MatchTier.TITLE_STARTS_WITH, tier("Café del Mar", "CAFE"))
        assertEquals(listOf("don", "t", "stop"), SearchText.tokens("Don't… stop!"))
    }

    @Test
    fun `fts query uses prefixes and empty input searches nothing`() {
        assertEquals("let* go*", SearchText.ftsQuery(SearchText.tokens("Let Go")))
        assertNull(SearchText.ftsQuery(SearchText.tokens(" !? ")))
    }

    @Test
    fun `highlights cover every match, positions line up with accented text`() {
        assertEquals(listOf(4..5), SearchText.highlights("Let Go", SearchText.tokens("go")))
        assertEquals(listOf(0..3), SearchText.highlights("Café", SearchText.tokens("cafe")))
    }

    @Test
    fun `typo forgiveness follows the length rules`() {
        assertEquals(0, Fuzzy.allowedDistance(3))
        assertEquals(1, Fuzzy.allowedDistance(4))
        assertEquals(1, Fuzzy.allowedDistance(7))
        assertEquals(2, Fuzzy.allowedDistance(8))
        assertTrue(Fuzzy.isCloseMatch("Beatels", "The Beatles"))
        assertFalse(Fuzzy.isCloseMatch("Bea", "Bee"))
        assertFalse(Fuzzy.isCloseMatch("Beatles", "The Beatles"))
        assertEquals(1, Fuzzy.distance("beatels", "beatles", 2))
        assertEquals(3, Fuzzy.distance("abcdef", "uvwxyz", 2))
    }
}
