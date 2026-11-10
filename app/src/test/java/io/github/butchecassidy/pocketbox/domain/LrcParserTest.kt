// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain

import io.github.butchecassidy.pocketbox.domain.lyrics.LrcParser
import io.github.butchecassidy.pocketbox.domain.model.LyricLine
import io.github.butchecassidy.pocketbox.domain.model.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun `parses timestamps, fractions and metadata`() {
        val lyrics = LrcParser.parse("\uFEFF[ar:Someone]\n[ti:Song]\n[00:12.30]First\n[00:15.5]Second\n[01:02:25]Third") as Lyrics.Synced
        assertEquals(listOf(LyricLine(12_300, "First"), LyricLine(15_500, "Second"), LyricLine(62_250, "Third")), lyrics.lines)
    }

    @Test
    fun `repeated stamps produce one line each, sorted`() {
        val lyrics = LrcParser.parse("[00:30.00][00:10.00]Chorus\n[00:20.000]Verse") as Lyrics.Synced
        assertEquals(listOf(10_000L, 20_000L, 30_000L), lyrics.lines.map { it.timeMs })
        assertEquals("Chorus", lyrics.lines.first().text)
    }

    @Test
    fun `offset shifts lines earlier and enhanced word stamps are removed`() {
        val lyrics = LrcParser.parse("[offset:+500]\n[00:01.00]<00:01.00>Hello <00:01.50>world") as Lyrics.Synced
        assertEquals(LyricLine(500, "Hello world"), lyrics.lines.single())
    }

    @Test
    fun `plain text becomes unsynced lyrics`() {
        assertEquals(Lyrics.Unsynced("Just words\nNo times"), LrcParser.parse("Just words\nNo times"))
    }

    @Test
    fun `empty or garbage input`() {
        assertNull(LrcParser.parse("   \n "))
        assertTrue(LrcParser.parse("[00:xx]broken") is Lyrics.Unsynced)
    }

    @Test
    fun `current line lookup`() {
        val lyrics = Lyrics.Synced(listOf(LyricLine(1_000, "a"), LyricLine(5_000, "b"), LyricLine(9_000, "c")))
        assertEquals(-1, lyrics.indexAt(500))
        assertEquals(0, lyrics.indexAt(1_000))
        assertEquals(1, lyrics.indexAt(8_999))
        assertEquals(2, lyrics.indexAt(60_000))
    }
}
