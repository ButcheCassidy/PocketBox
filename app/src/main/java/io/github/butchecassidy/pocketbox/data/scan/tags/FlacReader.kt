// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.scan.tags

import io.github.butchecassidy.pocketbox.domain.model.AudioCodec
import io.github.butchecassidy.pocketbox.domain.model.BitrateMode

/** Reads native FLAC files: STREAMINFO, Vorbis comments and pictures. */
object FlacReader {
    private const val STREAMINFO = 0
    private const val VORBIS_COMMENT = 4
    private const val PICTURE = 6
    private const val MAX_BLOCKS = 1_000

    /** [start] is where `fLaC` begins (after any ID3 tag some taggers wrongly put in front). */
    fun read(reader: BinaryReader, start: Long, tags: RawTags): StreamInfo {
        reader.position = start
        if (reader.fourCc() != "fLaC") throw MalformedAudioException("Not FLAC")
        var info = StreamInfo(AudioCodec.FLAC)
        var blocks = 0
        while (blocks++ < MAX_BLOCKS) {
            val header = reader.u8()
            val isLast = header and 0x80 != 0
            val type = header and 0x7F
            val length = reader.u24be().toLong()
            val blockStart = reader.position
            if (blockStart + length > reader.size) throw MalformedAudioException("Truncated FLAC block")
            when (type) {
                STREAMINFO -> info = readStreamInfo(reader)
                VORBIS_COMMENT -> runCatching { VorbisCommentReader.read(reader, tags) }
                PICTURE -> runCatching { VorbisCommentReader.readFlacPicture(reader, length, tags) }
            }
            reader.position = blockStart + length
            if (isLast) break
        }
        return info.copy(bitrateBps = averageBitrate(reader.size - reader.position, info.durationMs))
    }

    private fun readStreamInfo(reader: BinaryReader): StreamInfo {
        reader.skip(10) // block sizes and frame sizes
        val packed = reader.u64be()
        val sampleRate = (packed ushr 44).toInt()
        val channels = ((packed ushr 41) and 0x7).toInt() + 1
        val bitsPerSample = ((packed ushr 36) and 0x1F).toInt() + 1
        val totalSamples = packed and 0xFFFFFFFFFL
        val durationMs = if (sampleRate > 0 && totalSamples > 0) totalSamples * 1000 / sampleRate else null
        return StreamInfo(
            codec = AudioCodec.FLAC,
            sampleRateHz = sampleRate.takeIf { it > 0 },
            bitDepth = bitsPerSample,
            bitrateMode = BitrateMode.AVERAGE,
            channels = channels,
            durationMs = durationMs,
        )
    }
}
