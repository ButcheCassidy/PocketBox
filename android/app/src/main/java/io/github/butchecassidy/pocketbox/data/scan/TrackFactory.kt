// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.scan

import io.github.butchecassidy.pocketbox.data.db.TrackEntity
import io.github.butchecassidy.pocketbox.data.scan.tags.RawTags
import io.github.butchecassidy.pocketbox.data.scan.tags.StreamInfo
import io.github.butchecassidy.pocketbox.domain.model.ArtSource
import io.github.butchecassidy.pocketbox.domain.model.QualityClassifier
import io.github.butchecassidy.pocketbox.domain.sort.SortKeys
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** Where a file sits and what is next to it, as found while walking the folder. */
data class FileContext(
    val folderId: Long,
    val documentUri: String,
    val documentId: String,
    val parentUri: String,
    val folderPath: String,
    val albumFolderId: String,
    val fileName: String,
    val fileSize: Long,
    val lastModified: Long,
    val folderArt: SidecarFile?,
    val lyricsFile: SidecarFile?,
)

data class SidecarFile(val uri: String, val lastModified: Long)

/** Turns tags and stream info into a [TrackEntity], applying Pocket Box's grouping and sorting rules. */
object TrackFactory {
    const val UNKNOWN_ARTIST = "Unknown artist"
    const val UNKNOWN_ALBUM = "Unknown album"
    const val VARIOUS_ARTISTS = "Various Artists"
    private val combiningMarks = Regex("\\p{Mn}+")
    private val whitespace = Regex("\\s+")
    private val leadingNumber = Regex("^\\s*(\\d{1,4})")
    private val yearPattern = Regex("(?<!\\d)(\\d{4})(?!\\d)")
    private val decibels = Regex("^\\s*([+-]?\\d+(?:[.,]\\d+)?)")

    fun build(
        file: FileContext,
        stream: StreamInfo,
        tags: RawTags,
        existingId: Long,
        dateAdded: Long,
        minDurationMs: Long,
    ): TrackEntity {
        val title = tags.first(RawTags.TITLE) ?: file.fileName.substringBeforeLast('.').ifBlank { file.fileName }
        val artist = joined(tags, RawTags.ARTIST) ?: UNKNOWN_ARTIST
        val album = tags.first(RawTags.ALBUM) ?: UNKNOWN_ALBUM
        val albumArtistTag = joined(tags, RawTags.ALBUM_ARTIST)
        val isCompilation = flag(tags.first(RawTags.COMPILATION))
        val albumArtist = albumArtistTag ?: if (isCompilation) VARIOUS_ARTISTS else artist
        val albumArtistSortTag = tags.first(RawTags.ALBUM_ARTIST_SORT)
            ?: if (albumArtistTag == null) tags.first(RawTags.ARTIST_SORT) else null
        val durationMs = stream.durationMs ?: 0L
        val embeddedArt = tags.hasPicture
        val art = when {
            embeddedArt -> SidecarFile(file.documentUri, file.lastModified) to ArtSource.EMBEDDED
            file.folderArt != null -> file.folderArt to ArtSource.FOLDER
            else -> null
        }

        return TrackEntity(
            id = existingId,
            folderId = file.folderId,
            documentUri = file.documentUri,
            documentId = file.documentId,
            parentUri = file.parentUri,
            folderPath = file.folderPath,
            fileName = file.fileName,
            fileSize = file.fileSize,
            lastModified = file.lastModified,
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            albumArtistTag = albumArtistTag,
            genre = joined(tags, RawTags.GENRE),
            composer = joined(tags, RawTags.COMPOSER),
            year = year(tags.first(RawTags.DATE)),
            trackNumber = number(tags.first(RawTags.TRACK_NUMBER)),
            discNumber = number(tags.first(RawTags.DISC_NUMBER)),
            durationMs = durationMs,
            codec = stream.codec.name,
            sampleRate = stream.sampleRateHz,
            bitDepth = stream.bitDepth,
            bitrate = stream.bitrateBps,
            bitrateMode = stream.bitrateMode.ordinal,
            channels = stream.channels,
            qualityClass = QualityClassifier.classify(stream.codec, stream.sampleRateHz, stream.bitDepth)?.ordinal,
            rgTrackGain = gain(tags.first(RawTags.RG_TRACK_GAIN)),
            rgTrackPeak = peak(tags.first(RawTags.RG_TRACK_PEAK)),
            rgAlbumGain = gain(tags.first(RawTags.RG_ALBUM_GAIN)),
            rgAlbumPeak = peak(tags.first(RawTags.RG_ALBUM_PEAK)),
            artUri = art?.first?.uri,
            artSource = art?.second?.ordinal,
            artVersion = art?.first?.lastModified ?: 0,
            lrcUri = file.lyricsFile?.uri,
            hasEmbeddedLyrics = tags.hasLyrics,
            releaseTypeTag = tags.first(RawTags.RELEASE_TYPE),
            isCompilation = isCompilation,
            albumKey = albumKey(album, albumArtistTag, file.albumFolderId),
            artistKey = artistKey(albumArtist),
            titleSortKey = SortKeys.key(title, tags.first(RawTags.TITLE_SORT)),
            albumSortKey = SortKeys.key(album, tags.first(RawTags.ALBUM_SORT)),
            albumArtistSortKey = SortKeys.key(albumArtist, albumArtistSortTag),
            fileNameSortKey = SortKeys.key(file.fileName),
            albumSortTag = tags.first(RawTags.ALBUM_SORT),
            albumArtistSortTag = albumArtistSortTag,
            titleLetter = SortKeys.letter(title, tags.first(RawTags.TITLE_SORT)),
            isHidden = durationMs < minDurationMs,
            dateAdded = dateAdded,
        )
    }

    /**
     * Groups tracks into releases. With an album-artist tag, the same album title by the same album
     * artist is one release wherever its files live. Without one, the folder decides, so a compilation
     * whose tracks each name a different artist stays together instead of scattering.
     */
    fun albumKey(album: String, albumArtistTag: String?, albumFolderId: String): String =
        hash(normalize(album) + "\u001F" + (albumArtistTag?.let(::normalize) ?: "dir:$albumFolderId"))

    fun artistKey(albumArtist: String): String = hash(normalize(albumArtist))

    /** Lower-case, accents removed, whitespace collapsed: "  Beyoncé " → "beyonce". */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(combiningMarks, "")
            .lowercase(Locale.ROOT).replace(whitespace, " ").trim()

    private fun hash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.take(KEY_BYTES).joinToString("") { "%02x".format(it) }
    }

    private fun joined(tags: RawTags, key: String): String? =
        tags.all(key).distinct().joinToString(", ").ifEmpty { null }

    /** "4/12" → 4, "07" → 7. Zero and garbage → null. */
    fun number(value: String?): Int? =
        value?.let { leadingNumber.find(it)?.groupValues?.get(1)?.toIntOrNull() }?.takeIf { it > 0 }

    /** "2003-05-12", "2003", "May 2003" → 2003. */
    fun year(value: String?): Int? =
        value?.let { yearPattern.find(it)?.groupValues?.get(1)?.toIntOrNull() }?.takeIf { it in 1000..2999 }

    /** "-6.54 dB" → -6.54. Values outside a sane range are ignored. */
    fun gain(value: String?): Float? =
        value?.let { decibels.find(it)?.groupValues?.get(1)?.replace(',', '.')?.toFloatOrNull() }
            ?.takeIf { it in -60f..60f }

    fun peak(value: String?): Float? =
        value?.trim()?.replace(',', '.')?.toFloatOrNull()?.takeIf { it > 0f && it < 100f }

    fun flag(value: String?): Boolean = value?.trim()?.lowercase(Locale.ROOT) in setOf("1", "true", "yes")

    private const val KEY_BYTES = 16
}
