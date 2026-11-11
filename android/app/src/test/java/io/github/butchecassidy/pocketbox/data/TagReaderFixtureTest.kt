// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data

import io.github.butchecassidy.pocketbox.data.scan.tags.ArrayByteSource
import io.github.butchecassidy.pocketbox.data.scan.tags.AudioFileReader
import io.github.butchecassidy.pocketbox.data.scan.tags.RawTags
import io.github.butchecassidy.pocketbox.data.scan.tags.ReadOptions
import io.github.butchecassidy.pocketbox.domain.model.AudioCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reads real (tiny) files of every supported container with Pocket Box's own tag readers. */
class TagReaderFixtureTest {
    private fun read(name: String, options: ReadOptions = ReadOptions.SCAN) =
        requireNotNull(AudioFileReader.read(ArrayByteSource(Fixtures.bytes(name)), options)) { "$name was not recognised" }

    @Test
    fun `every container gives codec, rate, channels, duration and tags`() {
        val expected = mapOf(
            "tone.mp3" to (AudioCodec.MP3 to 22_050),
            "tone.flac" to (AudioCodec.FLAC to 22_050),
            "tone.ogg" to (AudioCodec.VORBIS to 22_050),
            "tone.opus" to (AudioCodec.OPUS to 48_000),
            "tone.m4a" to (AudioCodec.AAC to 22_050),
            "tone.wav" to (AudioCodec.WAV_PCM to 8_000),
            "tone.aiff" to (AudioCodec.AIFF to 8_000),
        )
        for ((name, codecAndRate) in expected) {
            val parsed = read(name)
            assertEquals(name, codecAndRate.first, parsed.stream.codec)
            assertEquals(name, codecAndRate.second, parsed.stream.sampleRateHz)
            assertEquals(name, 1, parsed.stream.channels)
            val duration = parsed.stream.durationMs
            assertNotNull(name, duration)
            assertTrue("$name lasts $duration ms", duration!! in 200L..700L)
            assertEquals(name, "Fixture", parsed.tags.first(RawTags.TITLE))
            assertEquals(name, "Tester", parsed.tags.first(RawTags.ARTIST))
        }
    }

    @Test
    fun `embedded covers are found and read only when asked`() {
        val cover = Fixtures.bytes("cover.png")
        for (name in listOf("tone.mp3", "tone.flac", "tone.m4a")) {
            val scan = read(name)
            assertTrue(name, scan.tags.hasPicture)
            assertNull(name, scan.tags.picture)
            assertArrayEquals(name, cover, read(name, ReadOptions.PICTURE).tags.picture?.data)
        }
    }

    @Test
    fun `truncated and random files never crash`() {
        for (name in Fixtures.audioFiles) {
            val bytes = Fixtures.bytes(name)
            for (cut in listOf(4, 12, 64, bytes.size / 2)) {
                runCatching { AudioFileReader.read(ArrayByteSource(bytes.copyOf(cut))) }
                    .onFailure { assertTrue("$name cut at $cut threw ${it.javaClass.simpleName}", it is java.io.IOException) }
            }
        }
        assertNull(AudioFileReader.read(ArrayByteSource(ByteArray(4096) { (it * 31).toByte() })))
    }
}
