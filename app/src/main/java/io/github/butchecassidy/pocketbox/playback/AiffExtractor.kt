// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.Util
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import io.github.butchecassidy.pocketbox.data.scan.tags.PcmContainerReader
import kotlin.math.max
import kotlin.math.min

/** Media3's built-in extractors plus [AiffExtractor], which Media3 doesn't ship. */
class PocketExtractorsFactory : ExtractorsFactory {
    private val defaults = DefaultExtractorsFactory()

    override fun createExtractors(): Array<Extractor> = defaults.createExtractors() + AiffExtractor()

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        defaults.createExtractors(uri, responseHeaders) + AiffExtractor()
}

/**
 * Plays uncompressed AIFF and AIFF-C (16/24/32-bit integer, big- or little-endian, and 32-bit float).
 * Samples are passed through untouched; Media3's audio sink converts the byte order.
 */
class AiffExtractor : Extractor {
    private lateinit var output: ExtractorOutput
    private lateinit var track: TrackOutput
    private var format: Header? = null
    private var pendingBytes = 0
    private var framesWritten = 0L
    private var startTimeUs = 0L

    private class Header(
        val channels: Int,
        val sampleRate: Int,
        val bytesPerFrame: Int,
        val encoding: Int,
        val dataStart: Long,
        val dataEnd: Long,
    ) {
        val frameCount: Long get() = (dataEnd - dataStart) / bytesPerFrame
        val durationUs: Long get() = Util.scaleLargeTimestamp(frameCount, C.MICROS_PER_SECOND, sampleRate.toLong())
        val targetSampleBytes: Int get() = max(bytesPerFrame, sampleRate / SAMPLES_PER_SECOND * bytesPerFrame)
    }

    override fun sniff(input: ExtractorInput): Boolean {
        val head = ParsableByteArray(12)
        if (!input.peekFully(head.data, 0, 12, true)) return false
        val form = head.readString(4)
        head.skipBytes(4)
        val type = head.readString(4)
        return form == "FORM" && (type == "AIFF" || type == "AIFC")
    }

    override fun init(output: ExtractorOutput) {
        this.output = output
        track = output.track(0, C.TRACK_TYPE_AUDIO)
        output.endTracks()
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val header = format ?: readHeader(input).also { header ->
            format = header
            output.seekMap(AiffSeekMap(header))
            track.format(
                Format.Builder()
                    .setContainerMimeType(MimeTypes.AUDIO_RAW)
                    .setSampleMimeType(MimeTypes.AUDIO_RAW)
                    .setChannelCount(header.channels)
                    .setSampleRate(header.sampleRate)
                    .setPcmEncoding(header.encoding)
                    .setAverageBitrate(header.sampleRate * header.bytesPerFrame * 8)
                    .setMaxInputSize(header.targetSampleBytes)
                    .build(),
            )
            track.durationUs(header.durationUs)
            if (input.position != header.dataStart) {
                seekPosition.position = header.dataStart
                return Extractor.RESULT_SEEK
            }
        }
        if (input.position < header.dataStart) {
            seekPosition.position = header.dataStart
            return Extractor.RESULT_SEEK
        }

        var bytesLeft = header.dataEnd - input.position
        while (bytesLeft > 0 && pendingBytes < header.targetSampleBytes) {
            val read = track.sampleData(input, min(header.targetSampleBytes - pendingBytes.toLong(), bytesLeft).toInt(), true)
            if (read == C.RESULT_END_OF_INPUT) {
                bytesLeft = 0
            } else {
                pendingBytes += read
                bytesLeft -= read
            }
        }
        val frames = pendingBytes / header.bytesPerFrame
        if (frames > 0) {
            val timeUs = startTimeUs + Util.scaleLargeTimestamp(framesWritten, C.MICROS_PER_SECOND, header.sampleRate.toLong())
            val size = frames * header.bytesPerFrame
            track.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, size, pendingBytes - size, null)
            framesWritten += frames
            pendingBytes -= size
        }
        return if (bytesLeft <= 0) Extractor.RESULT_END_OF_INPUT else Extractor.RESULT_CONTINUE
    }

    override fun seek(position: Long, timeUs: Long) {
        pendingBytes = 0
        framesWritten = 0
        startTimeUs = timeUs
    }

    override fun release() = Unit

    /** Walks the chunks up to the start of the sound data. Unsupported encodings fail cleanly. */
    private fun readHeader(input: ExtractorInput): Header {
        val buffer = ParsableByteArray(12)
        input.readFully(buffer.data, 0, 12)
        buffer.skipBytes(8)
        val isAifc = buffer.readString(4) == "AIFC"
        var channels = 0
        var bitsPerSample = 0
        var sampleRate = 0
        var compression = "NONE"
        var chunks = 0
        while (chunks++ < MAX_CHUNKS) {
            val chunkHeader = ParsableByteArray(8)
            input.readFully(chunkHeader.data, 0, 8)
            val id = chunkHeader.readString(4)
            val size = chunkHeader.readUnsignedInt()
            when (id) {
                "COMM" -> {
                    val body = ParsableByteArray(size.toInt().coerceIn(18, MAX_COMM))
                    input.readFully(body.data, 0, body.limit())
                    channels = body.readUnsignedShort()
                    body.skipBytes(4)
                    bitsPerSample = body.readUnsignedShort()
                    val extended = ByteArray(10).also { body.readBytes(it, 0, 10) }
                    sampleRate = PcmContainerReader.readExtended(extended).toInt()
                    if (isAifc && body.bytesLeft() >= 4) compression = body.readString(4)
                    input.skipFully((size - body.limit() + (size and 1)).toInt())
                }
                "SSND" -> {
                    val offset = ParsableByteArray(8).also { input.readFully(it.data, 0, 8) }.readUnsignedInt()
                    input.skipFully(offset.toInt())
                    val encoding = encodingFor(compression, bitsPerSample)
                    if (channels <= 0 || sampleRate <= 0 || encoding == C.ENCODING_INVALID) {
                        throw androidx.media3.common.ParserException.createForUnsupportedContainerFeature("AIFF encoding")
                    }
                    val start = input.position
                    val end = min(start + size - 8 - offset, input.length.takeIf { it != C.LENGTH_UNSET.toLong() } ?: Long.MAX_VALUE)
                    return Header(channels, sampleRate, channels * (bitsPerSample / 8), encoding, start, end)
                }
                else -> input.skipFully((size + (size and 1)).toInt())
            }
        }
        throw androidx.media3.common.ParserException.createForMalformedContainer("No SSND chunk", null)
    }

    private fun encodingFor(compression: String, bits: Int): Int = when (compression) {
        "NONE", "twos" -> when (bits) {
            16 -> C.ENCODING_PCM_16BIT_BIG_ENDIAN
            24 -> C.ENCODING_PCM_24BIT_BIG_ENDIAN
            32 -> C.ENCODING_PCM_32BIT_BIG_ENDIAN
            else -> C.ENCODING_INVALID
        }
        "sowt" -> when (bits) {
            16 -> C.ENCODING_PCM_16BIT
            24 -> C.ENCODING_PCM_24BIT
            32 -> C.ENCODING_PCM_32BIT
            else -> C.ENCODING_INVALID
        }
        "fl32", "FL32" -> C.ENCODING_PCM_FLOAT_BIG_ENDIAN
        else -> C.ENCODING_INVALID
    }

    /** Uncompressed audio: every frame has the same size, so seeking is simple arithmetic. */
    private class AiffSeekMap(private val header: Header) : SeekMap {
        override fun isSeekable() = header.frameCount > 0
        override fun getDurationUs() = header.durationUs
        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
            val frame = (timeUs * header.sampleRate / C.MICROS_PER_SECOND).coerceIn(0, max(0, header.frameCount - 1))
            val frameTimeUs = Util.scaleLargeTimestamp(frame, C.MICROS_PER_SECOND, header.sampleRate.toLong())
            return SeekMap.SeekPoints(SeekPoint(frameTimeUs, header.dataStart + frame * header.bytesPerFrame))
        }
    }

    private companion object {
        const val SAMPLES_PER_SECOND = 10
        const val MAX_CHUNKS = 1_000
        const val MAX_COMM = 64
    }
}
