// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.scan.tags

import io.github.butchecassidy.pocketbox.domain.model.AudioCodec
import io.github.butchecassidy.pocketbox.domain.model.BitrateMode

/** Reads Ogg Vorbis and Opus files: identification header, comments and duration. */
object OggReader {
    private const val OPUS_RATE = 48_000
    private const val TAIL_SCAN = 256 * 1024
    private const val MAX_PAGES = 4_096

    private class Page(val dataStart: Long, val dataLength: Long, val serial: Long, val granule: Long)

    fun read(reader: BinaryReader, tags: RawTags): StreamInfo {
        val first = readPage(reader, 0) ?: throw MalformedAudioException("No Ogg page")
        reader.position = first.dataStart
        val id = reader.readFully(minOf(first.dataLength, 64).toInt())
        val isOpus = id.startsWithAscii("OpusHead")
        val stream = when {
            id.startsWithAscii("\u0001vorbis") -> readVorbisId(id)
            isOpus -> readOpusId(id)
            else -> throw MalformedAudioException("Unsupported Ogg codec")
        }
        // Opus streams start with "pre-skip" samples that are decoded but never played.
        val preSkip = if (isOpus) BinaryReader(ArrayByteSource(id), 10).u16le().toLong() else 0L

        val packets = OggPacketInput(reader, first.dataStart + first.dataLength, first.serial)
        runCatching {
            val magic = if (isOpus) "OpusTags" else "\u0003vorbis"
            if (String(packets.readFully(magic.length), Charsets.ISO_8859_1) == magic) {
                VorbisCommentReader.read(packets, tags)
            }
        }

        val granule = lastGranule(reader, first.serial)
        val durationMs = when {
            granule <= 0 -> null
            isOpus -> ((granule - preSkip) * 1000 / OPUS_RATE).takeIf { it > 0 }
            else -> stream.sampleRateHz?.let { granule * 1000 / it }
        }
        return stream.copy(
            durationMs = durationMs,
            bitrateBps = averageBitrate(reader.size, durationMs),
            bitrateMode = if (stream.bitrateMode == BitrateMode.UNKNOWN) BitrateMode.AVERAGE else stream.bitrateMode,
        )
    }

    private fun readVorbisId(id: ByteArray): StreamInfo {
        val r = BinaryReader(ArrayByteSource(id), 11)
        val channels = r.u8()
        val rate = r.u32le().toInt()
        val max = r.u32le().toInt()
        val nominal = r.u32le().toInt()
        val min = r.u32le().toInt()
        // The encoder states CBR only when all three bitrates match.
        val mode = if (nominal > 0 && max == nominal && min == nominal) BitrateMode.CONSTANT else BitrateMode.VARIABLE
        return StreamInfo(AudioCodec.VORBIS, sampleRateHz = rate, channels = channels, bitrateMode = mode)
    }

    private fun readOpusId(id: ByteArray): StreamInfo {
        val r = BinaryReader(ArrayByteSource(id), 9)
        val channels = r.u8()
        // Opus always decodes at 48 kHz, whatever rate the original recording had.
        return StreamInfo(AudioCodec.OPUS, sampleRateHz = OPUS_RATE, channels = channels)
    }

    private fun readPage(reader: BinaryReader, at: Long): Page? {
        val header = reader.peek(at, 27)
        if (header.size < 27 || !header.startsWithAscii("OggS")) return null
        val r = BinaryReader(ArrayByteSource(header), 6)
        val granule = r.u64le()
        val serial = r.u32le()
        val segments = header[26].toInt() and 0xFF
        val lacing = reader.peek(at + 27, segments)
        if (lacing.size < segments) return null
        val dataLength = lacing.sumOf { it.toInt() and 0xFF }.toLong()
        return Page(at + 27 + segments, dataLength, serial, granule)
    }

    /** The granule position of the last page of the stream: its length in samples. */
    private fun lastGranule(reader: BinaryReader, serial: Long): Long {
        val from = (reader.size - TAIL_SCAN).coerceAtLeast(0)
        val tail = reader.peek(from, (reader.size - from).toInt())
        var i = tail.size - 27
        while (i >= 0) {
            if (tail.startsWithAscii("OggS", i)) {
                val r = BinaryReader(ArrayByteSource(tail), i + 6L)
                val granule = r.u64le()
                if (r.u32le() == serial && granule != -1L) return granule
            }
            i--
        }
        return -1
    }

    /** Presents the data of consecutive pages of one stream as one continuous input. */
    private class OggPacketInput(
        private val reader: BinaryReader,
        private var nextPageAt: Long,
        private val serial: Long,
    ) : SequentialInput {
        private var pagePosition = 0L
        private var pageRemaining = 0L
        private var pagesRead = 0

        private fun ensure() {
            while (pageRemaining == 0L) {
                if (pagesRead++ > MAX_PAGES) throw MalformedAudioException("Too many Ogg pages")
                val page = readPage(reader, nextPageAt) ?: throw MalformedAudioException("Ogg ended")
                nextPageAt = page.dataStart + page.dataLength
                if (page.serial == serial) {
                    pagePosition = page.dataStart
                    pageRemaining = page.dataLength
                }
            }
        }

        override fun readFully(count: Int): ByteArray {
            if (count < 0 || count > BinaryReader.MAX_SINGLE_READ) throw MalformedAudioException("Bad length")
            val out = ByteArray(count)
            var done = 0
            while (done < count) {
                ensure()
                val n = minOf(pageRemaining, (count - done).toLong()).toInt()
                reader.position = pagePosition
                System.arraycopy(reader.readFully(n), 0, out, done, n)
                done += n
                pagePosition += n
                pageRemaining -= n
            }
            return out
        }

        override fun skip(count: Long) {
            var left = count
            while (left > 0) {
                ensure()
                val n = minOf(left, pageRemaining)
                pagePosition += n
                pageRemaining -= n
                left -= n
            }
        }

        override fun u32le(): Long {
            val b = readFully(4)
            return (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
                ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
        }
    }
}
