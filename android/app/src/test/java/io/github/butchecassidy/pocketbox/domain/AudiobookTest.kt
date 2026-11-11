// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain

import io.github.butchecassidy.pocketbox.domain.model.AudioCodec
import io.github.butchecassidy.pocketbox.domain.model.AudioQuality
import io.github.butchecassidy.pocketbox.domain.model.Audiobook
import io.github.butchecassidy.pocketbox.domain.model.BitrateMode
import io.github.butchecassidy.pocketbox.domain.model.BookProgress
import io.github.butchecassidy.pocketbox.domain.model.ReplayGain
import io.github.butchecassidy.pocketbox.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookTest {
    private fun chapter(id: Long, minutes: Long) = Track(
        id = id, uri = "content://book/$id", title = "Chapter $id", artist = "Author", album = "Book", albumArtist = "Author",
        albumKey = "book", artistKey = "author", genre = null, composer = null, year = null, trackNumber = id.toInt(),
        discNumber = null, fileName = "$id.m4b", folderPath = "Books/Book",
        quality = AudioQuality(AudioCodec.AAC, 44_100, null, 64_000, BitrateMode.CONSTANT, 2, 1, minutes * 60_000),
        replayGain = ReplayGain.NONE, art = null, lyricsUri = null, hasEmbeddedLyrics = false, dateAddedMs = 0,
        sortLetter = "C", isAudiobook = true,
    )

    private val chapters = listOf(chapter(1, 10), chapter(2, 20), chapter(3, 30))
    private fun book(progress: BookProgress?) = Audiobook("book", "Book", "Author", null, chapters, progress)

    @Test
    fun `listened time counts earlier chapters plus the spot in the current one`() {
        val book = book(BookProgress(trackId = 2, positionMs = 5 * 60_000, updatedAtMs = 0))
        assertEquals(1, book.progressChapter)
        assertEquals(15 * 60_000L, book.listenedMs)
        assertEquals(60 * 60_000L, book.durationMs)
        assertFalse(book.isFinished)
    }

    @Test
    fun `never started, a removed chapter, and the last half minute`() {
        assertEquals(-1, book(null).progressChapter)
        assertEquals(0L, book(null).listenedMs)
        assertEquals(-1, book(BookProgress(trackId = 99, positionMs = 1_000, updatedAtMs = 0)).progressChapter)
        assertTrue(book(BookProgress(trackId = 3, positionMs = 30 * 60_000 - 20_000, updatedAtMs = 0)).isFinished)
    }
}
