// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.scan

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import io.github.butchecassidy.pocketbox.data.scan.integrity.IntegrityFinding
import io.github.butchecassidy.pocketbox.data.scan.integrity.IntegrityInspector
import io.github.butchecassidy.pocketbox.data.scan.tags.AudioFileReader
import io.github.butchecassidy.pocketbox.data.scan.tags.FileChannelSource
import io.github.butchecassidy.pocketbox.data.scan.tags.ParsedAudio
import io.github.butchecassidy.pocketbox.data.scan.tags.RawTags
import io.github.butchecassidy.pocketbox.data.scan.tags.ReadOptions
import io.github.butchecassidy.pocketbox.data.scan.tags.StreamInfo
import io.github.butchecassidy.pocketbox.domain.model.AudioCodec
import io.github.butchecassidy.pocketbox.domain.model.BitrateMode
import java.io.FileInputStream
import java.io.InputStream

/**
 * Opens audio files through the Storage Access Framework and reads them. Every file is treated as
 * untrusted: failures of any kind return null instead of crashing.
 */
class AudioFiles(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /** Reads stream info and tags with Pocket Box's own readers, falling back to Android's retriever. */
    fun read(uri: Uri, options: ReadOptions = ReadOptions.SCAN): ParsedAudio? =
        readOwn(uri, options) ?: readWithPlatform(uri, options)

    /**
     * The integrity check (see IntegrityInspector): what the file holds besides music, tags, art and
     * lyrics. Empty when it's clean; null when the file can't be opened at all.
     */
    fun inspect(uri: Uri): List<IntegrityFinding>? = runCatching {
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { stream ->
                FileChannelSource(stream.channel).use(IntegrityInspector::inspect)
            }
        }
    }.getOrNull()

    private fun readOwn(uri: Uri, options: ReadOptions): ParsedAudio? = runCatching {
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { stream ->
                FileChannelSource(stream.channel).use { AudioFileReader.read(it, options) }
            }
        }
    }.getOrNull()

    /** Last resort for formats the built-in readers don't cover (for example raw AAC or Matroska audio). */
    private fun readWithPlatform(uri: Uri, options: ReadOptions): ParsedAudio? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(appContext, uri)
            fun meta(key: Int): String? = retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }
            if (meta(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes") return null
            val tags = RawTags(options)
            meta(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { tags.add(RawTags.TITLE, it) }
            meta(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { tags.add(RawTags.ARTIST, it) }
            meta(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { tags.add(RawTags.ALBUM, it) }
            meta(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.let { tags.add(RawTags.ALBUM_ARTIST, it) }
            meta(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let { tags.add(RawTags.GENRE, it) }
            meta(MediaMetadataRetriever.METADATA_KEY_COMPOSER)?.let { tags.add(RawTags.COMPOSER, it) }
            meta(MediaMetadataRetriever.METADATA_KEY_YEAR)?.let { tags.add(RawTags.DATE, it) }
            meta(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.let { tags.add(RawTags.TRACK_NUMBER, it) }
            meta(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.let { tags.add(RawTags.DISC_NUMBER, it) }
            meta(MediaMetadataRetriever.METADATA_KEY_COMPILATION)?.let { tags.add(RawTags.COMPILATION, it) }
            val picture = if (options.wantPicture) retriever.embeddedPicture else null
            if (picture != null) tags.offerPicture(RawTags.FRONT_COVER, picture)
            val bitrate = meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            val mime = meta(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
            val stream = StreamInfo(
                codec = when {
                    mime.contains("mpeg") -> AudioCodec.MP3
                    mime.contains("aac") || mime.contains("mp4") -> AudioCodec.AAC
                    mime.contains("flac") -> AudioCodec.FLAC
                    mime.contains("opus") -> AudioCodec.OPUS
                    mime.contains("vorbis") || mime.contains("ogg") -> AudioCodec.VORBIS
                    else -> AudioCodec.UNKNOWN
                },
                sampleRateHz = meta(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull(),
                bitDepth = meta(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull(),
                bitrateBps = bitrate,
                bitrateMode = if (bitrate != null) BitrateMode.AVERAGE else BitrateMode.UNKNOWN,
                durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
            )
            ParsedAudio(stream, tags)
        }
    }.getOrNull()

    /** Reads a small file (cover image, .lrc) with a hard size cap; null if missing or too large. */
    fun readSmallFile(uri: Uri, maxBytes: Int): ByteArray? = runCatching {
        resolver.openInputStream(uri)?.use { it.readCapped(maxBytes) }
    }.getOrNull()

    private fun InputStream.readCapped(maxBytes: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        var total = 0
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            total += n
            if (total > maxBytes) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val BUFFER = 16 * 1024
    }
}
