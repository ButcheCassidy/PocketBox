// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.scan.tags

import java.util.Base64

/** Reads Vorbis comments (FLAC, Ogg Vorbis, Opus) and the FLAC picture structure they may embed. */
object VorbisCommentReader {
    private const val MAX_FIELDS = 10_000
    private const val MAX_TEXT_FIELD = 1024 * 1024
    private const val KEY_PEEK = 64
    private val pictureKeys = setOf("METADATA_BLOCK_PICTURE", "COVERART")

    fun read(input: SequentialInput, tags: RawTags) {
        val vendorLength = input.u32le()
        input.skip(vendorLength)
        val count = input.u32le().coerceAtMost(MAX_FIELDS.toLong()).toInt()
        repeat(count) {
            val length = input.u32le()
            if (length <= 0) return@repeat
            val head = input.readFully(minOf(length, KEY_PEEK.toLong()).toInt())
            val equals = head.indexOf('='.code.toByte())
            if (equals <= 0) {
                input.skip(length - head.size)
                return@repeat
            }
            val key = RawTags.canonicalKey(String(head, 0, equals, Charsets.UTF_8))
            val restLength = length - head.size
            when {
                key in pictureKeys -> readPicture(input, head, equals, restLength, key, tags)
                key == RawTags.LYRICS && !tags.wantsLyrics -> {
                    tags.markLyrics()
                    input.skip(restLength)
                }
                length > MAX_TEXT_FIELD -> input.skip(restLength)
                else -> {
                    val value = head.copyOfRange(equals + 1, head.size) + input.readFully(restLength.toInt())
                    tags.add(key, String(value, Charsets.UTF_8))
                }
            }
        }
    }

    private fun readPicture(
        input: SequentialInput,
        head: ByteArray,
        equals: Int,
        restLength: Long,
        key: String,
        tags: RawTags,
    ) {
        // Base64 grows data by 4/3; refuse anything that would decode beyond the picture size cap.
        if (!tags.wantsPicture || restLength > BinaryReader.MAX_SINGLE_READ / 3L * 4) {
            tags.offerPicture(0, null)
            input.skip(restLength)
            return
        }
        val encoded = head.copyOfRange(equals + 1, head.size) + input.readFully(restLength.toInt())
        val decoded = runCatching { Base64.getMimeDecoder().decode(encoded) }.getOrNull()
        if (decoded == null) {
            tags.offerPicture(0, null)
        } else if (key == "COVERART") {
            tags.offerPicture(RawTags.FRONT_COVER, decoded)
        } else {
            readFlacPicture(BinaryReader(ArrayByteSource(decoded)), decoded.size.toLong(), tags)
        }
    }

    /** The FLAC PICTURE block layout, also used inside METADATA_BLOCK_PICTURE. */
    fun readFlacPicture(reader: BinaryReader, length: Long, tags: RawTags) {
        val end = reader.position + length
        val type = reader.u32be().toInt()
        if (!tags.wantsPicture) {
            tags.offerPicture(type, null)
            return
        }
        reader.skip(reader.u32be()) // MIME type
        reader.skip(reader.u32be()) // description
        reader.skip(16) // width, height, colour depth, palette size
        val dataLength = reader.u32be()
        if (dataLength <= 0 || reader.position + dataLength > end) return tags.offerPicture(type, null)
        tags.offerPicture(type, reader.readFully(dataLength.toInt()))
    }
}
