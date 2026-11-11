// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.scan.tags

import io.github.butchecassidy.pocketbox.domain.model.AudioCodec
import io.github.butchecassidy.pocketbox.domain.model.BitrateMode

/** Reads MPEG audio (MP3, MP2) frame headers and the Xing/Info/VBRI header that states VBR or CBR. */
object Mp3Reader {
    private const val SYNC_SEARCH = 128 * 1024

    private val bitratesV1 = arrayOf(
        intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448), // Layer I
        intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384), // Layer II
        intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320), // Layer III
    )
    private val bitratesV2 = arrayOf(
        intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256), // Layer I
        intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160), // Layers II and III
    )
    private val sampleRates = mapOf(
        3 to intArrayOf(44_100, 48_000, 32_000), // MPEG-1
        2 to intArrayOf(22_050, 24_000, 16_000), // MPEG-2
        0 to intArrayOf(11_025, 12_000, 8_000), // MPEG-2.5
    )

    /** One decoded frame header. */
    private class Frame(val version: Int, val layer: Int, val bitrateKbps: Int, val sampleRate: Int, val mono: Boolean, val length: Int) {
        val samplesPerFrame: Int = when {
            layer == 1 -> 384
            layer == 3 && version != 3 -> 576
            else -> 1152
        }
    }

    private fun parseHeader(h: ByteArray, at: Int = 0): Frame? {
        if (h.size < at + 4) return null
        val b1 = h[at + 1].toInt() and 0xFF
        val b2 = h[at + 2].toInt() and 0xFF
        val b3 = h[at + 3].toInt() and 0xFF
        if (h[at].toInt() and 0xFF != 0xFF || b1 and 0xE0 != 0xE0) return null
        val version = (b1 shr 3) and 3
        val layerBits = (b1 shr 1) and 3
        val bitrateIndex = b2 shr 4
        val rateIndex = (b2 shr 2) and 3
        if (version == 1 || layerBits == 0 || bitrateIndex == 0 || bitrateIndex == 15 || rateIndex == 3) return null
        val layer = 4 - layerBits
        val bitrate = if (version == 3) bitratesV1[layer - 1][bitrateIndex] else bitratesV2[if (layer == 1) 0 else 1][bitrateIndex]
        val sampleRate = sampleRates.getValue(version)[rateIndex]
        val padding = (b2 shr 1) and 1
        val length = when {
            layer == 1 -> (12 * bitrate * 1000 / sampleRate + padding) * 4
            layer == 3 && version != 3 -> 72 * bitrate * 1000 / sampleRate + padding
            else -> 144 * bitrate * 1000 / sampleRate + padding
        }
        return Frame(version, layer, bitrate, sampleRate, mono = (b3 shr 6) == 3, length = length)
    }

    fun read(reader: BinaryReader, audioStart: Long, hasId3v1: Boolean): StreamInfo {
        val window = reader.peek(audioStart, SYNC_SEARCH)
        var offset = -1
        var frame: Frame? = null
        for (i in 0 until window.size - 4) {
            val candidate = parseHeader(window, i) ?: continue
            // Require the next frame to line up too, so random bytes aren't mistaken for audio.
            val next = i + candidate.length
            val nextHeader = if (next + 4 <= window.size) parseHeader(window, next) else null
            if (nextHeader != null || next + 4 > window.size) {
                offset = i
                frame = candidate
                break
            }
        }
        if (frame == null) throw MalformedAudioException("No MPEG frame found")

        val frameStart = audioStart + offset
        val audioEnd = reader.size - if (hasId3v1) 128 else 0
        val audioBytes = audioEnd - frameStart
        val sideInfo = when {
            frame.version == 3 -> if (frame.mono) 17 else 32
            else -> if (frame.mono) 9 else 17
        }
        val xing = reader.peek(frameStart + 4 + sideInfo, 16)
        val vbri = reader.peek(frameStart + 4 + 32, 18)

        var mode = BitrateMode.UNKNOWN
        var frames: Long? = null
        var bytes: Long? = null
        if (xing.startsWithAscii("Xing") || xing.startsWithAscii("Info")) {
            mode = if (xing.startsWithAscii("Info")) BitrateMode.CONSTANT else BitrateMode.VARIABLE
            val r = BinaryReader(ArrayByteSource(xing), 4)
            val flags = r.u32be()
            if (flags and 1L != 0L && xing.size >= 12) frames = r.u32be()
            if (flags and 2L != 0L && xing.size >= 16) bytes = r.u32be()
        } else if (vbri.startsWithAscii("VBRI")) {
            mode = BitrateMode.VARIABLE
            val r = BinaryReader(ArrayByteSource(vbri), 10)
            bytes = r.u32be()
            frames = r.u32be()
        }

        val durationMs = when {
            frames != null && frames > 0 -> frames * frame.samplesPerFrame * 1000 / frame.sampleRate
            frame.bitrateKbps > 0 -> audioBytes * 8 / frame.bitrateKbps
            else -> null
        }
        val bitrate = when (mode) {
            BitrateMode.VARIABLE -> averageBitrate(bytes?.takeIf { it > 0 } ?: audioBytes, durationMs)
            else -> frame.bitrateKbps * 1000
        }
        return StreamInfo(
            codec = if (frame.layer == 3) AudioCodec.MP3 else AudioCodec.MP2,
            sampleRateHz = frame.sampleRate,
            bitrateBps = bitrate,
            bitrateMode = mode,
            channels = if (frame.mono) 1 else 2,
            durationMs = durationMs,
        )
    }

    /** True when an MPEG frame header starts at the beginning of [bytes]. */
    fun startsWithFrame(bytes: ByteArray): Boolean = parseHeader(bytes) != null

    /** Length in bytes of the MPEG frame whose header starts at [at], or null if there is no valid header. */
    internal fun frameLength(bytes: ByteArray, at: Int): Int? = parseHeader(bytes, at)?.length
}
