// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.scan.tags

import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Thrown when a file is truncated or its structure makes no sense. Always caught by the scanner. */
class MalformedAudioException(message: String) : IOException(message)

/** Random-access bytes: a file on disk, or an array in tests. */
interface ByteSource : Closeable {
    val size: Long

    /** Reads up to [length] bytes at [position] into [buffer]; returns how many were read (0 at the end). */
    fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
}

/** A file opened through the Storage Access Framework. */
class FileChannelSource(private val channel: FileChannel) : ByteSource {
    override val size: Long = channel.size()

    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= size || length == 0) return 0
        val read = channel.read(ByteBuffer.wrap(buffer, offset, length), position)
        return if (read < 0) 0 else read
    }

    override fun close() = channel.close()
}

/** In-memory bytes, used for de-unsynchronised ID3 tags and in tests. */
class ArrayByteSource(private val bytes: ByteArray) : ByteSource {
    override val size: Long get() = bytes.size.toLong()

    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= bytes.size) return 0
        val count = minOf(length.toLong(), bytes.size - position).toInt()
        System.arraycopy(bytes, position.toInt(), buffer, offset, count)
        return count
    }

    override fun close() = Unit
}

/** Sequential input that the Vorbis comment reader needs; implemented by [BinaryReader] and Ogg packets. */
interface SequentialInput {
    fun readFully(count: Int): ByteArray
    fun skip(count: Long)
    fun u32le(): Long
}

/**
 * A buffered cursor over a [ByteSource]. Every read is bounds-checked against the file size and
 * against [MAX_SINGLE_READ], so a lying length field can never cause a huge allocation.
 */
class BinaryReader(val source: ByteSource, var position: Long = 0) : SequentialInput {
    private val buffer = ByteArray(BUFFER_SIZE)
    private var bufferStart = -1L
    private var bufferLength = 0

    val size: Long get() = source.size
    val remaining: Long get() = (source.size - position).coerceAtLeast(0)

    fun u8(): Int {
        if (position !in bufferStart until bufferStart + bufferLength) fill()
        return buffer[(position++ - bufferStart).toInt()].toInt() and 0xFF
    }

    fun u16be(): Int = (u8() shl 8) or u8()
    fun u16le(): Int = u8() or (u8() shl 8)
    fun u24be(): Int = (u8() shl 16) or (u8() shl 8) or u8()
    fun u32be(): Long = (u16be().toLong() shl 16) or u16be().toLong()
    override fun u32le(): Long = u16le().toLong() or (u16le().toLong() shl 16)
    fun u64be(): Long = (u32be() shl 32) or u32be()
    fun u64le(): Long = u32le() or (u32le() shl 32)

    /** ID3v2 "syncsafe" integer: 4 bytes of 7 bits each. */
    fun syncsafe32(): Int = (u8() shl 21) or (u8() shl 14) or (u8() shl 7) or u8()

    fun fourCc(): String = String(readFully(4), Charsets.ISO_8859_1)

    override fun readFully(count: Int): ByteArray {
        if (count < 0 || count > MAX_SINGLE_READ) throw MalformedAudioException("Bad read length $count")
        if (count > remaining) throw MalformedAudioException("Unexpected end of file")
        val out = ByteArray(count)
        var done = 0
        while (done < count) {
            val inBuffer = position in bufferStart until bufferStart + bufferLength
            if (inBuffer) {
                val start = (position - bufferStart).toInt()
                val n = minOf(count - done, bufferLength - start)
                System.arraycopy(buffer, start, out, done, n)
                done += n
                position += n
            } else {
                val n = source.read(position, out, done, count - done)
                if (n <= 0) throw MalformedAudioException("Unexpected end of file")
                done += n
                position += n
            }
        }
        return out
    }

    override fun skip(count: Long) {
        if (count < 0) throw MalformedAudioException("Negative skip")
        position += count
    }

    /** Reads bytes at [at] without moving the cursor; returns fewer bytes near the end of the file. */
    fun peek(at: Long, count: Int): ByteArray {
        val n = minOf(count.toLong(), (size - at).coerceAtLeast(0)).toInt()
        val out = ByteArray(n)
        var done = 0
        while (done < n) {
            val read = source.read(at + done, out, done, n - done)
            if (read <= 0) break
            done += read
        }
        return if (done == n) out else out.copyOf(done)
    }

    private fun fill() {
        if (position >= size) throw MalformedAudioException("Unexpected end of file")
        bufferStart = position
        bufferLength = 0
        while (bufferLength == 0) {
            val read = source.read(position, buffer, 0, BUFFER_SIZE)
            if (read <= 0) throw MalformedAudioException("Unexpected end of file")
            bufferLength = read
        }
    }

    companion object {
        private const val BUFFER_SIZE = 8 * 1024

        /** No single structure Pocket Box reads may be larger than this (album art is the largest). */
        const val MAX_SINGLE_READ = 16 * 1024 * 1024
    }
}

/** Starts with the given ASCII magic? */
internal fun ByteArray.startsWithAscii(magic: String, at: Int = 0): Boolean {
    if (size < at + magic.length) return false
    return magic.indices.all { this[at + it] == magic[it].code.toByte() }
}
