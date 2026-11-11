// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data

import java.io.ByteArrayOutputStream

/**
 * The tiny test files in src/test/resources/fixtures: half-second sine tones made with ffmpeg, with
 * title "Fixture", artist "Tester", album "Tests" and (MP3, FLAC, M4A) an 8×8 PNG cover.
 */
object Fixtures {
    val audioFiles = listOf("tone.mp3", "tone.flac", "tone.ogg", "tone.opus", "tone.m4a", "tone.wav", "tone.aiff")

    fun bytes(name: String): ByteArray =
        requireNotNull(Fixtures::class.java.getResourceAsStream("/fixtures/$name")) { "missing fixture $name" }.use { it.readBytes() }

    /** An ID3v2.3 tag holding [frames] (frame ID to frame body). */
    fun id3v23(vararg frames: Pair<String, ByteArray>): ByteArray {
        val body = ByteArrayOutputStream()
        for ((id, data) in frames) {
            body.write(id.toByteArray(Charsets.ISO_8859_1))
            body.write(be32(data.size))
            body.write(byteArrayOf(0, 0))
            body.write(data)
        }
        val size = body.size()
        val syncsafe = byteArrayOf((size shr 21 and 0x7F).toByte(), (size shr 14 and 0x7F).toByte(), (size shr 7 and 0x7F).toByte(), (size and 0x7F).toByte())
        return "ID3".toByteArray() + byteArrayOf(3, 0, 0) + syncsafe + body.toByteArray()
    }

    /** An APIC (attached picture) frame body: front cover, PNG, no description. */
    fun apic(image: ByteArray): ByteArray = byteArrayOf(0) + "image/png".toByteArray() + byteArrayOf(0, 3, 0) + image

    /** A text frame body in ISO-8859-1. */
    fun text(value: String): ByteArray = byteArrayOf(0) + value.toByteArray(Charsets.ISO_8859_1)

    fun be32(value: Int) = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())
    fun le32(value: Int) = byteArrayOf(value.toByte(), (value ushr 8).toByte(), (value ushr 16).toByte(), (value ushr 24).toByte())

    /** The start of a ZIP archive (what an APK or JAR looks like), followed by filler. */
    val zipLike: ByteArray = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4) + ByteArray(200) { (it * 7).toByte() }
}
