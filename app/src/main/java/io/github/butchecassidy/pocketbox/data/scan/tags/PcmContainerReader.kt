// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.scan.tags

import io.github.butchecassidy.pocketbox.domain.model.AudioCodec
import io.github.butchecassidy.pocketbox.domain.model.BitrateMode
import kotlin.math.pow

/** Reads WAV (RIFF/RF64) and AIFF/AIFF-C files: PCM format, length and embedded tags. */
object PcmContainerReader {
    private const val MAX_CHUNKS = 10_000
    private const val MAX_TEXT_CHUNK = 64 * 1024
    private val infoKeys = mapOf(
        "INAM" to RawTags.TITLE,
        "IART" to RawTags.ARTIST,
        "IPRD" to RawTags.ALBUM,
        "IGNR" to RawTags.GENRE,
        "ICRD" to RawTags.DATE,
        "ITRK" to RawTags.TRACK_NUMBER,
        "IPRT" to RawTags.TRACK_NUMBER,
    )

    /** Facts about the PCM stream, filled in as chunks are read. */
    private class Pcm {
        var codec = AudioCodec.UNKNOWN
        var channels = 0
        var sampleRate = 0
        var bitDepth = 0
        var byteRate = 0L
        var dataBytes = 0L
        var frames = 0L
    }

    fun readWav(reader: BinaryReader, tags: RawTags): StreamInfo {
        reader.position = 0
        val isRf64 = reader.fourCc() == "RF64"
        reader.skip(4)
        if (reader.fourCc() != "WAVE") throw MalformedAudioException("Not WAVE")
        val pcm = Pcm()
        var rf64DataSize = -1L
        var at = 12L
        var chunks = 0
        while (at + 8 <= reader.size && chunks++ < MAX_CHUNKS) {
            reader.position = at
            val id = reader.fourCc()
            val size = reader.u32le()
            val body = at + 8
            when (id) {
                "ds64" -> {
                    reader.skip(8)
                    rf64DataSize = reader.u64le()
                }
                "fmt " -> readWavFormat(reader, pcm)
                // Streaming writers sometimes leave a bogus size; never claim more audio than the file holds.
                "data" -> pcm.dataBytes = (if (isRf64 && size == 0xFFFFFFFFL && rf64DataSize >= 0) rf64DataSize else size)
                    .coerceAtMost(reader.size - body)
                "LIST" -> if (size >= 4 && reader.fourCc() == "INFO") readInfo(reader, body + 4, body + size, tags)
                "id3 ", "ID3 " -> Id3Reader.read(reader, body, tags)
            }
            val length = if (id == "data" && pcm.dataBytes > size) pcm.dataBytes else size
            at = body + length + (length and 1)
        }
        val durationMs = if (pcm.byteRate > 0) pcm.dataBytes * 1000 / pcm.byteRate else null
        return finish(pcm, durationMs, pcm.byteRate * 8)
    }

    private fun readWavFormat(reader: BinaryReader, pcm: Pcm) {
        var format = reader.u16le()
        pcm.channels = reader.u16le()
        pcm.sampleRate = reader.u32le().toInt()
        pcm.byteRate = reader.u32le()
        reader.skip(2) // block align
        pcm.bitDepth = reader.u16le()
        if (format == 0xFFFE) { // WAVE_FORMAT_EXTENSIBLE
            reader.skip(2) // extension size
            val validBits = reader.u16le()
            if (validBits > 0) pcm.bitDepth = validBits
            reader.skip(4) // channel mask
            format = reader.u16le() // first two bytes of the sub-format GUID
        }
        pcm.codec = when (format) {
            1 -> AudioCodec.WAV_PCM
            3 -> AudioCodec.WAV_FLOAT
            2, 0x11 -> AudioCodec.WAV_ADPCM
            else -> AudioCodec.UNKNOWN
        }
    }

    private fun readInfo(reader: BinaryReader, from: Long, to: Long, tags: RawTags) {
        var at = from
        var chunks = 0
        while (at + 8 <= to && chunks++ < MAX_CHUNKS) {
            reader.position = at
            val id = reader.fourCc()
            val size = reader.u32le()
            val key = infoKeys[id]
            if (key != null && size in 1..MAX_TEXT_CHUNK) {
                tags.add(key, String(reader.readFully(size.toInt()), Charsets.UTF_8).substringBefore('\u0000'))
            }
            at += 8 + size + (size and 1)
        }
    }

    fun readAiff(reader: BinaryReader, tags: RawTags): StreamInfo {
        reader.position = 8
        val isAifc = reader.fourCc() == "AIFC"
        val pcm = Pcm()
        var at = 12L
        var chunks = 0
        while (at + 8 <= reader.size && chunks++ < MAX_CHUNKS) {
            reader.position = at
            val id = reader.fourCc()
            val size = reader.u32be()
            val body = at + 8
            when (id) {
                "COMM" -> readCommon(reader, pcm, isAifc)
                "SSND" -> pcm.dataBytes = (size - 8).coerceIn(0, reader.size - body)
                "ID3 ", "id3 " -> Id3Reader.read(reader, body, tags)
                "NAME" -> readText(reader, size, RawTags.TITLE, tags)
                "AUTH" -> readText(reader, size, RawTags.ARTIST, tags)
            }
            at = body + size + (size and 1)
        }
        val durationMs = if (pcm.sampleRate > 0 && pcm.frames > 0) pcm.frames * 1000 / pcm.sampleRate else null
        return finish(pcm, durationMs, pcm.sampleRate.toLong() * pcm.channels * pcm.bitDepth)
    }

    private fun readCommon(reader: BinaryReader, pcm: Pcm, isAifc: Boolean) {
        pcm.channels = reader.u16be()
        pcm.frames = reader.u32be()
        pcm.bitDepth = reader.u16be()
        pcm.sampleRate = readExtended(reader.readFully(10)).toInt()
        val compression = if (isAifc) reader.fourCc() else "NONE"
        pcm.codec = when (compression) {
            "NONE", "twos", "sowt" -> AudioCodec.AIFF
            "fl32", "FL32", "fl64", "FL64" -> AudioCodec.AIFF_FLOAT
            else -> AudioCodec.UNKNOWN
        }
    }

    private fun readText(reader: BinaryReader, size: Long, key: String, tags: RawTags) {
        if (size in 1..MAX_TEXT_CHUNK) tags.add(key, String(reader.readFully(size.toInt()), Charsets.ISO_8859_1))
    }

    /** The 80-bit IEEE 754 extended float AIFF uses for its sample rate. */
    internal fun readExtended(bytes: ByteArray): Double {
        val exponent = ((bytes[0].toInt() and 0x7F) shl 8) or (bytes[1].toInt() and 0xFF)
        var mantissa = 0.0
        for (i in 2 until 10) mantissa = mantissa * 256 + (bytes[i].toInt() and 0xFF)
        if (exponent == 0 && mantissa == 0.0) return 0.0
        return mantissa * 2.0.pow(exponent - 16383 - 63)
    }

    private fun finish(pcm: Pcm, durationMs: Long?, statedBitrate: Long): StreamInfo {
        val lossless = pcm.codec.isLossless == true
        return StreamInfo(
            codec = pcm.codec,
            sampleRateHz = pcm.sampleRate.takeIf { it > 0 },
            bitDepth = if (lossless) pcm.bitDepth.takeIf { it > 0 } else null,
            bitrateBps = statedBitrate.takeIf { it in 1..Int.MAX_VALUE }?.toInt(),
            bitrateMode = if (lossless) BitrateMode.CONSTANT else BitrateMode.UNKNOWN,
            channels = pcm.channels.takeIf { it > 0 },
            durationMs = durationMs?.takeIf { it > 0 },
        )
    }
}
