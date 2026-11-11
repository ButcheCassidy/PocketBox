// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain

import io.github.butchecassidy.pocketbox.domain.sort.SortKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SortKeysTest {
    private fun sorted(vararg titles: String) = titles.sortedWith { a, b -> SortKeys.compareKeys(SortKeys.key(a), SortKeys.key(b)) }

    @Test
    fun `leading articles are ignored`() {
        assertEquals(listOf("Abba", "The Beatles", "Coldplay"), sorted("Coldplay", "The Beatles", "Abba"))
        assertEquals(listOf("An Apple", "A Boy", "Zed"), sorted("Zed", "A Boy", "An Apple"))
    }

    @Test
    fun `an article alone is kept as the title`() {
        assertEquals("The", SortKeys.sortBase("The"))
    }

    @Test
    fun `case and accents do not change the order`() {
        assertEquals(0, SortKeys.compareKeys(SortKeys.key("Éclair"), SortKeys.key("eclair")))
        assertEquals(listOf("apple", "Éclair", "zebra"), sorted("zebra", "Éclair", "apple"))
    }

    @Test
    fun `numbers sort naturally`() {
        assertEquals(listOf("Track 2", "Track 10", "Track 100"), sorted("Track 100", "Track 10", "Track 2"))
    }

    @Test
    fun `sort tag replaces the display title`() {
        val tagged = SortKeys.key("Weird Title", sortTag = "Aardvark")
        assertTrue(SortKeys.compareKeys(tagged, SortKeys.key("Beta")) < 0)
    }

    @Test
    fun `stored keys and the in-memory comparator agree`() {
        val titles = listOf("The Zoo", "zoo 2", "Zoo 10", "Ångström", "#1 Hit", "abc")
        val byKey = titles.sortedWith { a, b -> SortKeys.compareKeys(SortKeys.key(a), SortKeys.key(b)) }
        assertEquals(titles.sortedWith(SortKeys.comparator), byKey)
    }

    @Test
    fun `letter headers fold accents and file symbols under hash`() {
        assertEquals("B", SortKeys.letter("The Beatles"))
        assertEquals("E", SortKeys.letter("éclair"))
        assertEquals("#", SortKeys.letter("99 Problems"))
        assertEquals("#", SortKeys.letter("(Intro)"))
    }
}
