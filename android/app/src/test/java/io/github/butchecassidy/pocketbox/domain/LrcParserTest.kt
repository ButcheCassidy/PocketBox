// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain

import io.github.butchecassidy.pocketbox.domain.lyrics.LrcParser
import io.github.butchecassidy.pocketbox.domain.model.LyricLine
import io.github.butchecassidy.pocketbox.domain.model.LyricWord
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
    fun `offset shifts lines and their word stamps earlier`() {
        val lyrics = LrcParser.parse("[offset:+500]\n[00:01.00]<00:01.00>Hello <00:01.50>world") as Lyrics.Synced
        assertEquals(LyricLine(500, "Hello world", listOf(LyricWord(500, "Hello "), LyricWord(1_000, "world"))), lyrics.lines.single())
    }

    @Test
    fun `word stamps after words, a closing stamp and a repeated line`() {
        val lyrics = LrcParser.parse("[00:02.00][00:10.00]Hey <00:02.40>you <00:03.00>") as Lyrics.Synced
        assertEquals(listOf(LyricWord(2_000, "Hey "), LyricWord(2_400, "you")), lyrics.lines[0].words)
        assertEquals(listOf(10_000L, 10_400L), lyrics.lines[1].words.map { it.timeMs })
        assertEquals(1, lyrics.lines[0].wordsSungAt(2_100))
        assertEquals(2, lyrics.lines[0].wordsSungAt(2_400))
    }

    @Test
    fun `word stamps in every fraction style, no spaces between words`() {
        val lyrics = LrcParser.parse("[00:01.00]<00:01:50>你<00:02.123>好<00:03>呀") as Lyrics.Synced
        assertEquals(listOf(LyricWord(1_500, "你"), LyricWord(2_123, "好"), LyricWord(3_000, "呀")), lyrics.lines.single().words)
        assertEquals("你好呀", lyrics.lines.single().text)
    }

    @Test
    fun `leading space and spaced A2 style keep the text the line shows`() {
        val line = (LrcParser.parse("[00:05.00] <00:05.00> One <00:05.50> two  ") as Lyrics.Synced).lines.single()
        assertEquals("One  two", line.text)
        assertEquals(listOf(LyricWord(5_000, "One "), LyricWord(5_500, " two")), line.words)
    }

    @Test
    fun `a line that is only stamps has no text and no words`() {
        val line = (LrcParser.parse("[00:07.00]<00:07.00><00:08.00>") as Lyrics.Synced).lines.single()
        assertEquals("", line.text)
        assertTrue(line.words.isEmpty())
    }

    @Test
    fun `negative offset moves words later and nothing goes below zero`() {
        val later = (LrcParser.parse("[offset:-250]\n[00:01.00]<00:01.00>a <00:01.20>b") as Lyrics.Synced).lines.single()
        assertEquals(listOf(1_250L, 1_450L), later.words.map { it.timeMs })
        val early = (LrcParser.parse("[offset:+5000]\n[00:01.00]<00:01.00>a <00:02.00>b") as Lyrics.Synced).lines.single()
        assertEquals(listOf(0L, 0L), early.words.map { it.timeMs })
    }

    @Test
    fun `text that only looks like a stamp stays text`() {
        val line = (LrcParser.parse("[00:01.00]I <3 you <1:2") as Lyrics.Synced).lines.single()
        assertEquals("I <3 you <1:2", line.text)
        assertTrue(line.words.isEmpty())
    }

    @Test
    fun `words light up in order and a mistimed word never skips ahead`() {
        val line = (LrcParser.parse("[00:01.00]<00:01.00>a <00:03.00>b <00:02.00>c") as Lyrics.Synced).lines.single()
        assertEquals(0, line.wordsSungAt(999))
        assertEquals(1, line.wordsSungAt(1_000))
        assertEquals(1, line.wordsSungAt(2_500))
        assertEquals(3, line.wordsSungAt(3_000))
    }

    @Test
    fun `lines without word stamps have no words`() {
        val lyrics = LrcParser.parse("[00:01.00]Plain line") as Lyrics.Synced
        assertTrue(lyrics.lines.single().words.isEmpty())
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
