// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain

import io.github.butchecassidy.pocketbox.domain.playlist.LibraryPath
import io.github.butchecassidy.pocketbox.domain.playlist.M3u
import io.github.butchecassidy.pocketbox.domain.playlist.M3uExportItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistPathTest {
    private val dir = "primary:Music/Playlists"

    @Test
    fun `relative entries resolve inside the volume`() {
        assertEquals("primary:Music/Rock/a.flac", LibraryPath.resolve("../Rock/a.flac", dir))
        assertEquals("primary:Music/Playlists/b.mp3", LibraryPath.resolve("./b.mp3", dir))
        assertEquals("primary:Music/Rock/c.mp3", LibraryPath.resolve("..\\Rock\\c.mp3", dir))
    }

    @Test
    fun `path traversal above the volume root is refused`() {
        assertNull(LibraryPath.resolve("../../../etc/passwd", dir))
        assertNull(LibraryPath.resolve("/storage/emulated/0/../../data/x", null))
    }

    @Test
    fun `absolute and file URIs map to shared storage only`() {
        assertEquals("primary:Music/a.mp3", LibraryPath.resolve("/storage/emulated/0/Music/a.mp3", null))
        assertEquals("primary:Music/a b.mp3", LibraryPath.resolve("file:///sdcard/Music/a%20b.mp3", null))
        assertEquals("1A2B-3C4D:Music/a.mp3", LibraryPath.resolve("/storage/1A2B-3C4D/Music/a.mp3", null))
        assertNull(LibraryPath.resolve("/data/data/other.app/secret", null))
        assertNull(LibraryPath.resolve("C:\\Music\\a.mp3", dir))
    }

    @Test
    fun `network, app schemes and control characters are refused`() {
        assertNull(LibraryPath.resolve("https://example.com/a.mp3", dir))
        assertNull(LibraryPath.resolve("content://evil/a.mp3", dir))
        assertNull(LibraryPath.resolve("a\u0000.mp3", dir))
        assertNull(LibraryPath.resolve("file:///sdcard/a%00.mp3", dir))
    }

    @Test
    fun `relative entries need a known playlist folder`() {
        assertNull(LibraryPath.resolve("a.mp3", null))
    }

    @Test
    fun `inside checks use whole folder names`() {
        assertTrue(LibraryPath.isInside("primary:Music/a.mp3", "primary:Music"))
        assertFalse(LibraryPath.isInside("primary:MusicOld/a.mp3", "primary:Music"))
        assertTrue(LibraryPath.isInside("primary:Music/a.mp3", "primary:"))
        assertFalse(LibraryPath.isInside("1A2B-3C4D:Music/a.mp3", "primary:Music"))
    }

    @Test
    fun `relativize and absolute paths for export`() {
        assertEquals("../Rock/a.flac", LibraryPath.relativize(dir, "primary:Music/Rock/a.flac"))
        assertNull(LibraryPath.relativize(dir, "1A2B-3C4D:Music/a.flac"))
        assertEquals("/storage/emulated/0/Music/a.mp3", LibraryPath.toAbsolute("primary:Music/a.mp3"))
    }

    @Test
    fun `m3u round trip`() {
        val text = M3u.write(listOf(M3uExportItem("../Rock/a.flac", "Artist", "Title", 61_500)))
        assertEquals("#EXTM3U\n#EXTINF:61,Artist - Title\n../Rock/a.flac\n", text)
        val entry = M3u.parse(text).single()
        assertEquals("../Rock/a.flac", entry.path)
        assertEquals("Artist - Title", entry.title)
        assertEquals(61, entry.durationSeconds)
    }

    @Test
    fun `m3u parsing skips comments and blank lines`() {
        val entries = M3u.parse("\uFEFF#EXTM3U\n\n# comment\nsong1.mp3\r\n  song2.flac  \n")
        assertEquals(listOf("song1.mp3", "song2.flac"), entries.map { it.path })
    }
}
