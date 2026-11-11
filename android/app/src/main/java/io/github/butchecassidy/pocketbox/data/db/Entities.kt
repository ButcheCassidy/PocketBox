// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
@file:Suppress("ArrayInDataClass") // Sort keys are byte arrays; these rows are never compared with ==.

package io.github.butchecassidy.pocketbox.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

/** A music folder the user granted through the system folder picker. */
@Entity(tableName = "folders", indices = [Index(value = ["treeUri"], unique = true)])
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val rootDocumentId: String,
    val displayName: String,
    val addedAt: Long,
    /** Everything inside is an audiobook: kept apart from the music in its own section. */
    @ColumnInfo(defaultValue = "0") val isAudiobooks: Boolean = false,
)

/**
 * A new file the integrity check held back because it contains something besides music, tags, art
 * and lyrics. It stays out of the library until the user taps "Add anyway" ([approved]).
 */
@Entity(
    tableName = "held_files",
    foreignKeys = [ForeignKey(FolderEntity::class, ["id"], ["folderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("folderId")],
)
data class HeldFileEntity(
    @PrimaryKey val documentUri: String,
    val folderId: Long,
    val fileName: String,
    val folderPath: String,
    val fileSize: Long,
    val lastModified: Long,
    /** One finding per line, see IntegrityFinding.encode(). */
    val findings: String,
    val heldAt: Long,
    val approved: Boolean = false,
)

/** A subfolder the user chose to leave out of the library. */
@Entity(
    tableName = "excluded_folders",
    foreignKeys = [ForeignKey(FolderEntity::class, ["id"], ["folderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["folderId", "documentId"], unique = true)],
)
data class ExcludedFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val documentId: String,
    val displayPath: String,
)

/** One audio file. Sort keys are java.text.Collator keys, so SQLite can sort by plain byte comparison. */
@Entity(
    tableName = "tracks",
    foreignKeys = [ForeignKey(FolderEntity::class, ["id"], ["folderId"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index(value = ["documentUri"], unique = true),
        Index("documentId"),
        Index("folderId"),
        Index("parentUri"),
        Index("albumKey"),
        Index("artistKey"),
        Index("dateAdded"),
        Index(value = ["isHidden", "titleSortKey"]),
    ],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val documentUri: String,
    val documentId: String,
    val parentUri: String,
    val folderPath: String,
    val fileName: String,
    val fileSize: Long,
    val lastModified: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val albumArtistTag: String?,
    val genre: String?,
    val composer: String?,
    val year: Int?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long,
    val codec: String,
    val sampleRate: Int?,
    val bitDepth: Int?,
    val bitrate: Int?,
    val bitrateMode: Int,
    val channels: Int?,
    val qualityClass: Int?,
    val rgTrackGain: Float?,
    val rgTrackPeak: Float?,
    val rgAlbumGain: Float?,
    val rgAlbumPeak: Float?,
    val artUri: String?,
    val artSource: Int?,
    val artVersion: Long,
    val lrcUri: String?,
    val hasEmbeddedLyrics: Boolean,
    val releaseTypeTag: String?,
    val isCompilation: Boolean,
    val albumKey: String,
    val artistKey: String,
    val titleSortKey: ByteArray,
    val albumSortKey: ByteArray,
    val albumArtistSortKey: ByteArray,
    val fileNameSortKey: ByteArray,
    val albumSortTag: String?,
    val albumArtistSortTag: String?,
    val titleLetter: String,
    val isHidden: Boolean,
    val dateAdded: Long,
    /** A chapter of an audiobook (its folder is marked as an audiobook folder), not a song. */
    @ColumnInfo(defaultValue = "0") val isAudiobook: Boolean = false,
)

/** Full-text index over the searchable text of [TrackEntity]; accents are folded (é matches e). */
@Fts4(
    contentEntity = TrackEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=2"],
)
@Entity(tableName = "tracks_fts")
data class TrackFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val genre: String?,
    val composer: String?,
)

/** An album, single or EP, rebuilt from [TrackEntity] after every scan. */
@Entity(
    tableName = "albums",
    indices = [Index("artistKey"), Index(value = ["albumArtistSortKey", "year", "albumSortKey"]), Index("dateAdded")],
)
data class AlbumEntity(
    @PrimaryKey val albumKey: String,
    val title: String,
    val albumArtist: String,
    val artistKey: String,
    val albumArtistSortKey: ByteArray,
    val albumSortKey: ByteArray,
    val year: Int?,
    val trackCount: Int,
    val discCount: Int,
    val durationMs: Long,
    val artUri: String?,
    val artSource: Int?,
    val artVersion: Long,
    val bestQuality: Int?,
    val autoReleaseType: Int,
    val dateAdded: Long,
)

/** An album artist, rebuilt from [TrackEntity] after every scan. */
@Entity(tableName = "artists", indices = [Index("sortKey")])
data class ArtistEntity(
    @PrimaryKey val artistKey: String,
    val name: String,
    val sortKey: ByteArray,
    val albumCount: Int,
    val trackCount: Int,
    val artUri: String?,
    val artSource: Int?,
    val artVersion: Long,
)

/** The user's choice of Album / Single / EP for one release, kept across rescans. */
@Entity(tableName = "release_overrides")
data class ReleaseOverrideEntity(
    @PrimaryKey val albumKey: String,
    val releaseType: Int,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(PlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["playlistId", "position"]), Index("trackId")],
)
data class PlaylistEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)

@Entity(
    tableName = "favorites",
    foreignKeys = [ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE)],
)
data class FavoriteEntity(
    @PrimaryKey val trackId: Long,
    val addedAt: Long,
)

/** Listening history. Stays on the device; excluded from backups like everything else. */
@Entity(
    tableName = "play_stats",
    foreignKeys = [ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("lastPlayedAt"), Index("playCount")],
)
data class PlayStatsEntity(
    @PrimaryKey val trackId: Long,
    val playCount: Int,
    val lastPlayedAt: Long,
)

@Entity(tableName = "recent_searches", indices = [Index("searchedAt")])
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long,
)

/** The saved play queue, so it survives the app being killed or the phone restarting. */
@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey val position: Int,
    val trackId: Long,
    val isManual: Boolean,
)

/** Where playback was in the saved queue. There is only ever one row (id 0). */
@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
    val shuffleOrder: String,
)

/** A custom equalizer preset the user saved. */
@Entity(tableName = "eq_presets", indices = [Index(value = ["name"], unique = true)])
data class EqPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bandsDb: String,
)

/** The user's lyrics timing fix for one song: positive shows the lyrics earlier. */
@Entity(
    tableName = "lyric_offsets",
    foreignKeys = [ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE)],
)
data class LyricOffsetEntity(
    @PrimaryKey val trackId: Long,
    val offsetMs: Long,
)

/** Where listening stopped in one audiobook ([bookKey] is its album key); saved automatically. */
@Entity(
    tableName = "book_progress",
    foreignKeys = [ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trackId")],
)
data class BookProgressEntity(
    @PrimaryKey val bookKey: String,
    val trackId: Long,
    val positionMs: Long,
    val updatedAt: Long,
)

/** A spot in an audiobook the user saved to come back to. */
@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookKey"), Index("trackId")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookKey: String,
    val trackId: Long,
    val positionMs: Long,
    val createdAt: Long,
)
