// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data

import io.github.butchecassidy.pocketbox.data.Fixtures.apic
import io.github.butchecassidy.pocketbox.data.Fixtures.id3v23
import io.github.butchecassidy.pocketbox.data.Fixtures.le32
import io.github.butchecassidy.pocketbox.data.Fixtures.text
import io.github.butchecassidy.pocketbox.data.Fixtures.zipLike
import io.github.butchecassidy.pocketbox.data.scan.integrity.IntegrityFinding
import io.github.butchecassidy.pocketbox.data.scan.integrity.IntegrityInspector
import io.github.butchecassidy.pocketbox.data.scan.integrity.IntegrityIssue
import io.github.butchecassidy.pocketbox.data.scan.tags.ArrayByteSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrityInspectorTest {
    private fun issues(bytes: ByteArray): Set<IntegrityIssue> = IntegrityInspector.inspect(ArrayByteSource(bytes)).map { it.issue }.toSet()

    private fun assertFound(bytes: ByteArray, vararg expected: IntegrityIssue) {
        val found = issues(bytes)
        expected.forEach { assertTrue("expected $it, found $found", it in found) }
    }

    @Test
    fun `normal files pass with no findings`() {
        for (name in Fixtures.audioFiles) {
            assertEquals(name, emptyList<IntegrityFinding>(), IntegrityInspector.inspect(ArrayByteSource(Fixtures.bytes(name))))
        }
    }

    @Test
    fun `an archive appended after the music is found in every format`() {
        for (name in Fixtures.audioFiles) {
            assertFound(Fixtures.bytes(name) + zipLike, IntegrityIssue.PROGRAM_CODE)
        }
        assertFound(Fixtures.bytes("tone.mp3") + zipLike, IntegrityIssue.DATA_AFTER_AUDIO)
        assertFound(Fixtures.bytes("tone.flac") + zipLike, IntegrityIssue.DATA_AFTER_AUDIO)
        assertFound(Fixtures.bytes("tone.ogg") + zipLike, IntegrityIssue.DATA_AFTER_AUDIO)
        assertFound(Fixtures.bytes("tone.wav") + zipLike, IntegrityIssue.DATA_AFTER_AUDIO)
    }

    @Test
    fun `trailing zero bytes and an ID3v1 tag are normal`() {
        val id3v1 = "TAG".toByteArray() + ByteArray(125)
        assertEquals(emptySet<IntegrityIssue>(), issues(Fixtures.bytes("tone.mp3") + ByteArray(300) + id3v1))
        assertEquals(emptySet<IntegrityIssue>(), issues(Fixtures.bytes("tone.flac") + id3v1))
    }

    @Test
    fun `an embedded file object in the tag is found`() {
        val geob = byteArrayOf(0) + "application/octet-stream".toByteArray() + byteArrayOf(0) + "x.bin".toByteArray() + byteArrayOf(0, 0) + zipLike
        assertFound(id3v23("GEOB" to geob) + Fixtures.bytes("tone.mp3"), IntegrityIssue.EMBEDDED_FILE, IntegrityIssue.PROGRAM_CODE)
    }

    @Test
    fun `album art must be a picture with nothing hidden after it`() {
        val cover = Fixtures.bytes("cover.png")
        assertEquals(emptySet<IntegrityIssue>(), issues(id3v23("APIC" to apic(cover)) + Fixtures.bytes("tone.mp3")))
        assertFound(id3v23("APIC" to apic(cover + zipLike)) + Fixtures.bytes("tone.mp3"), IntegrityIssue.DATA_AFTER_PICTURE, IntegrityIssue.PROGRAM_CODE)
        val elf = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()) + ByteArray(64)
        assertFound(id3v23("APIC" to apic(elf)) + Fixtures.bytes("tone.mp3"), IntegrityIssue.NOT_A_PICTURE, IntegrityIssue.PROGRAM_CODE)
    }

    @Test
    fun `script code in a text tag is found`() {
        assertFound(id3v23("TIT2" to text("Song<script>alert(1)</script>")) + Fixtures.bytes("tone.mp3"), IntegrityIssue.SCRIPT_IN_TAGS)
    }

    @Test
    fun `filled-in tag padding is found`() {
        val tag = id3v23("TIT2" to text("Song"))
        val padded = tag.copyOf(tag.size + 64).also { it[it.size - 10] = 1 }
        val size = padded.size - 10
        padded[6] = (size shr 21 and 0x7F).toByte(); padded[7] = (size shr 14 and 0x7F).toByte()
        padded[8] = (size shr 7 and 0x7F).toByte(); padded[9] = (size and 0x7F).toByte()
        assertFound(padded + Fixtures.bytes("tone.mp3"), IntegrityIssue.HIDDEN_IN_PADDING)
    }

    @Test
    fun `a FLAC application block is another program's data`() {
        val flac = Fixtures.bytes("tone.flac")
        // Insert after STREAMINFO (4-byte marker + 4-byte header + 34 bytes); STREAMINFO is never the last block here.
        val block = byteArrayOf(2, 0, 0, 8) + "TEST".toByteArray() + byteArrayOf(1, 2, 3, 4)
        val tampered = flac.copyOfRange(0, 42) + block + flac.copyOfRange(42, flac.size)
        assertFound(tampered, IntegrityIssue.APPLICATION_DATA)
    }

    @Test
    fun `a large unknown WAV chunk inside the file is found`() {
        val wav = Fixtures.bytes("tone.wav")
        val chunk = "zzzz".toByteArray() + le32(70_000) + ByteArray(70_000)
        val tampered = wav + chunk
        val riffSize = tampered.size - 8
        le32(riffSize).copyInto(tampered, 4)
        assertFound(tampered, IntegrityIssue.UNKNOWN_BLOCK)
    }

    @Test
    fun `MP4 data outside the box structure is found`() {
        val junk = byteArrayOf(0, 0, 0x10, 0) + byteArrayOf(1, 2, 3, 4) + ByteArray(64) { 9 }
        assertFound(Fixtures.bytes("tone.m4a") + junk, IntegrityIssue.UNKNOWN_BLOCK)
    }

    @Test
    fun `findings survive the round trip through the database text`() {
        val findings = listOf(IntegrityFinding(IntegrityIssue.EMBEDDED_FILE, "ID3 GEOB frame"), IntegrityFinding(IntegrityIssue.PROGRAM_CODE, "a ZIP archive in x"))
        assertEquals(findings, IntegrityFinding.decodeAll(IntegrityFinding.encodeAll(findings)))
    }
}
