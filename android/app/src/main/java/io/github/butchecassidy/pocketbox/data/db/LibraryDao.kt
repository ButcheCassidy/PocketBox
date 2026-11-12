// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Read-only library queries for screens, search and the media browser. Hidden (too short) songs never
 * appear, and audiobook chapters only appear in the audiobook queries at the end.
 */
@Dao
interface LibraryDao {
    // ---- Songs ----

    @Query("SELECT $TRACK_COLUMNS FROM tracks WHERE isHidden = 0 AND isAudiobook = 0 ORDER BY titleSortKey, title")
    fun songsByTitle(): Flow<List<TrackRow>>

    @Query("SELECT $TRACK_COLUMNS FROM tracks WHERE isHidden = 0 AND isAudiobook = 0 ORDER BY albumArtistSortKey, titleSortKey")
    fun songsByArtist(): Flow<List<TrackRow>>

    @Query("SELECT $TRACK_COLUMNS FROM tracks WHERE isHidden = 0 AND isAudiobook = 0 ORDER BY dateAdded DESC, titleSortKey")
    fun songsByDateAdded(): Flow<List<TrackRow>>

    @Query(
        "SELECT $TRACK_COLUMNS FROM tracks WHERE albumKey = :albumKey AND isHidden = 0 AND isAudiobook = 0 " +
            "ORDER BY COALESCE(discNumber, 1), trackNumber IS NULL, trackNumber, fileNameSortKey",
    )
    fun albumTracks(albumKey: String): Flow<List<TrackRow>>

    @Query(
        "SELECT $TRACK_COLUMNS FROM tracks WHERE artistKey = :artistKey AND isHidden = 0 AND isAudiobook = 0 ORDER BY titleSortKey, title",
    )
    fun artistTracks(artistKey: String): Flow<List<TrackRow>>

    @Query("SELECT $TRACK_COLUMNS FROM tracks WHERE parentUri = :parentUri AND isHidden = 0 AND isAudiobook = 0 ORDER BY fileNameSortKey")
    fun folderTracks(parentUri: String): Flow<List<TrackRow>>

    @Query("SELECT $TRACK_COLUMNS FROM tracks WHERE genre = :genre AND isHidden = 0 AND isAudiobook = 0 ORDER BY titleSortKey")
    fun genreTracks(genre: String): Flow<List<TrackRow>>

    @Query("SELECT $TRACK_COLUMNS FROM tracks WHERE year = :year AND isHidden = 0 AND isAudiobook = 0 ORDER BY titleSortKey")
    fun yearTracks(year: Int): Flow<List<TrackRow>>

    @Query("SELECT $TRACK_COLUMNS FROM tracks WHERE composer = :composer AND isHidden = 0 AND isAudiobook = 0 ORDER BY titleSortKey")
    fun composerTracks(composer: String): Flow<List<TrackRow>>

    @Query("SELECT $TRACK_COLUMNS FROM tracks WHERE id IN (:ids)")
    suspend fun tracksByIds(ids: List<Long>): List<TrackRow>

    @Query("SELECT $TRACK_COLUMNS FROM tracks WHERE id = :id")
    fun track(id: Long): Flow<TrackRow?>

    @Query("SELECT id, documentId FROM tracks WHERE documentId IN (:documentIds) AND isHidden = 0 AND isAudiobook = 0")
    suspend fun idsByDocumentIds(documentIds: List<String>): List<DocumentIdRow>

    @Query("SELECT documentId FROM tracks WHERE id = :id")
    suspend fun documentId(id: Long): String?

    @Query("SELECT COUNT(*) FROM tracks WHERE isHidden = 0 AND isAudiobook = 0")
    fun songCount(): Flow<Int>

    // ---- Search ----

    @Query(
        "SELECT $TRACK_COLUMNS FROM tracks JOIN tracks_fts ON tracks.id = tracks_fts.rowid " +
            "WHERE tracks_fts MATCH :match AND tracks.isHidden = 0 AND tracks.isAudiobook = 0 LIMIT :limit",
    )
    suspend fun search(match: String, limit: Int): List<TrackRow>

    /** Distinct names for typo-tolerant matching, small enough to keep in memory. */
    @Query(
        "SELECT title FROM tracks WHERE isHidden = 0 AND isAudiobook = 0 UNION SELECT albumArtist FROM tracks WHERE isHidden = 0 AND isAudiobook = 0 " +
            "UNION SELECT artist FROM tracks WHERE isHidden = 0 AND isAudiobook = 0 UNION SELECT album FROM tracks WHERE isHidden = 0 AND isAudiobook = 0",
    )
    suspend fun distinctNames(): List<String>

    @Query(
        "SELECT $TRACK_COLUMNS FROM tracks WHERE isHidden = 0 AND isAudiobook = 0 AND (title IN (:names) OR artist IN (:names) " +
            "OR albumArtist IN (:names) OR album IN (:names)) LIMIT :limit",
    )
    suspend fun tracksMatchingNames(names: List<String>, limit: Int): List<TrackRow>

    // ---- Albums ----

    @Query(
        "SELECT a.*, o.releaseType AS overrideType FROM albums a " +
            "LEFT JOIN release_overrides o ON o.albumKey = a.albumKey " +
            "WHERE COALESCE(o.releaseType, a.autoReleaseType) IN (:types) " +
            "ORDER BY a.albumArtistSortKey, a.year IS NULL, a.year, a.albumSortKey",
    )
    fun albumsByArtistYear(types: List<Int>): Flow<List<AlbumRow>>

    @Query(
        "SELECT a.*, o.releaseType AS overrideType FROM albums a " +
            "LEFT JOIN release_overrides o ON o.albumKey = a.albumKey " +
            "WHERE COALESCE(o.releaseType, a.autoReleaseType) IN (:types) ORDER BY a.albumSortKey, a.albumArtistSortKey",
    )
    fun albumsByTitle(types: List<Int>): Flow<List<AlbumRow>>

    @Query(
        "SELECT a.*, o.releaseType AS overrideType FROM albums a " +
            "LEFT JOIN release_overrides o ON o.albumKey = a.albumKey " +
            "WHERE COALESCE(o.releaseType, a.autoReleaseType) IN (:types) " +
            "ORDER BY a.year IS NULL, a.year DESC, a.albumArtistSortKey, a.albumSortKey",
    )
    fun albumsByYear(types: List<Int>): Flow<List<AlbumRow>>

    @Query(
        "SELECT a.*, o.releaseType AS overrideType FROM albums a " +
            "LEFT JOIN release_overrides o ON o.albumKey = a.albumKey " +
            "WHERE COALESCE(o.releaseType, a.autoReleaseType) IN (:types) ORDER BY a.dateAdded DESC, a.albumSortKey",
    )
    fun albumsByDateAdded(types: List<Int>): Flow<List<AlbumRow>>

    @Query(
        "SELECT a.*, o.releaseType AS overrideType FROM albums a " +
            "LEFT JOIN release_overrides o ON o.albumKey = a.albumKey WHERE a.albumKey = :albumKey",
    )
    fun album(albumKey: String): Flow<AlbumRow?>

    @Query(
        "SELECT a.*, o.releaseType AS overrideType FROM albums a " +
            "LEFT JOIN release_overrides o ON o.albumKey = a.albumKey WHERE a.albumKey IN (:albumKeys)",
    )
    suspend fun albumsByKeys(albumKeys: List<String>): List<AlbumRow>

    @Query(
        "SELECT a.*, o.releaseType AS overrideType FROM albums a " +
            "LEFT JOIN release_overrides o ON o.albumKey = a.albumKey " +
            "WHERE a.artistKey = :artistKey ORDER BY a.year IS NULL, a.year, a.albumSortKey",
    )
    fun albumsForArtist(artistKey: String): Flow<List<AlbumRow>>

    @Query(
        "SELECT a.*, o.releaseType AS overrideType FROM albums a " +
            "LEFT JOIN release_overrides o ON o.albumKey = a.albumKey ORDER BY a.dateAdded DESC LIMIT :limit",
    )
    fun recentlyAddedAlbums(limit: Int): Flow<List<AlbumRow>>

    @Query(
        "SELECT a.*, o.releaseType AS overrideType FROM albums a " +
            "JOIN (SELECT t.albumKey AS k, MAX(s.lastPlayedAt) AS lastPlayed FROM play_stats s " +
            "JOIN tracks t ON t.id = s.trackId GROUP BY t.albumKey) r ON r.k = a.albumKey " +
            "LEFT JOIN release_overrides o ON o.albumKey = a.albumKey ORDER BY r.lastPlayed DESC LIMIT :limit",
    )
    fun recentlyPlayedAlbums(limit: Int): Flow<List<AlbumRow>>

    @Query(
        "SELECT a.*, o.releaseType AS overrideType FROM albums a " +
            "JOIN (SELECT t.albumKey AS k, SUM(s.playCount) AS plays FROM play_stats s " +
            "JOIN tracks t ON t.id = s.trackId GROUP BY t.albumKey) r ON r.k = a.albumKey " +
            "LEFT JOIN release_overrides o ON o.albumKey = a.albumKey ORDER BY r.plays DESC LIMIT :limit",
    )
    fun mostPlayedAlbums(limit: Int): Flow<List<AlbumRow>>

    // ---- Artists ----

    @Query("SELECT * FROM artists ORDER BY sortKey")
    fun artists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE artistKey = :artistKey")
    fun artist(artistKey: String): Flow<ArtistEntity?>

    @Query("SELECT * FROM artists WHERE artistKey IN (:artistKeys)")
    suspend fun artistsByKeys(artistKeys: List<String>): List<ArtistEntity>

    // ---- Folders and facets ----

    @Query(
        "SELECT parentUri, folderPath, COUNT(*) AS trackCount FROM tracks WHERE isHidden = 0 AND isAudiobook = 0 " +
            "GROUP BY parentUri ORDER BY folderPath COLLATE NOCASE",
    )
    fun folders(): Flow<List<FolderRow>>

    @Query(
        "SELECT genre AS value, COUNT(*) AS trackCount FROM tracks WHERE isHidden = 0 AND isAudiobook = 0 AND genre IS NOT NULL " +
            "GROUP BY genre ORDER BY genre COLLATE NOCASE",
    )
    fun genres(): Flow<List<FacetRow>>

    @Query(
        "SELECT CAST(year AS TEXT) AS value, COUNT(*) AS trackCount FROM tracks WHERE isHidden = 0 AND isAudiobook = 0 " +
            "AND year IS NOT NULL GROUP BY year ORDER BY year DESC",
    )
    fun years(): Flow<List<FacetRow>>

    @Query(
        "SELECT composer AS value, COUNT(*) AS trackCount FROM tracks WHERE isHidden = 0 AND isAudiobook = 0 AND composer IS NOT NULL " +
            "GROUP BY composer ORDER BY composer COLLATE NOCASE",
    )
    fun composers(): Flow<List<FacetRow>>
}
