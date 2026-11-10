// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Writes made by the library scanner, plus the album/artist rebuild that follows each scan. */
@Dao
interface ScanDao {
    // ---- Folders ----

    @Query("SELECT * FROM folders ORDER BY displayName COLLATE NOCASE")
    fun folders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun foldersOnce(): List<FolderEntity>

    @Insert
    suspend fun insertFolder(folder: FolderEntity): Long

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Long)

    @Query("SELECT * FROM excluded_folders WHERE folderId = :folderId")
    suspend fun excludedFolders(folderId: Long): List<ExcludedFolderEntity>

    @Query("SELECT * FROM excluded_folders ORDER BY displayPath COLLATE NOCASE")
    fun allExcludedFolders(): Flow<List<ExcludedFolderEntity>>

    @Insert
    suspend fun insertExcludedFolder(folder: ExcludedFolderEntity)

    @Query("DELETE FROM excluded_folders WHERE id = :id")
    suspend fun deleteExcludedFolder(id: Long)

    // ---- Tracks ----

    @Query(
        "SELECT id, documentUri, lastModified, fileSize, artUri, artSource, artVersion, lrcUri, dateAdded " +
            "FROM tracks WHERE folderId = :folderId",
    )
    suspend fun scanIndex(folderId: Long): List<ScanIndexRow>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun trackCount(): Int

    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET artUri = :artUri, artSource = :artSource, artVersion = :artVersion, lrcUri = :lrcUri WHERE id = :id")
    suspend fun updateSidecars(id: Long, artUri: String?, artSource: Int?, artVersion: Long, lrcUri: String?)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteTracks(ids: List<Long>)

    @Query("UPDATE tracks SET isHidden = (durationMs < :minDurationMs)")
    suspend fun applyMinimumDuration(minDurationMs: Long)

    // ---- Album / artist rebuild ----

    @Query(
        "SELECT id, albumKey, artist, albumArtist, albumArtistTag, albumArtistSortTag, isCompilation FROM tracks",
    )
    suspend fun groupingRows(): List<GroupingRow>

    @Query("UPDATE tracks SET albumArtist = :albumArtist, artistKey = :artistKey, albumArtistSortKey = :sortKey WHERE id = :id")
    suspend fun updateAlbumArtist(id: Long, albumArtist: String, artistKey: String, sortKey: ByteArray)

    @Query(
        "SELECT albumKey, MIN(album) AS title, MIN(albumArtist) AS albumArtist, MIN(artistKey) AS artistKey, " +
            "MIN(albumSortTag) AS albumSortTag, MIN(albumArtistSortTag) AS albumArtistSortTag, MIN(year) AS year, " +
            "COUNT(*) AS trackCount, COUNT(DISTINCT COALESCE(discNumber, 1)) AS discCount, " +
            "SUM(durationMs) AS durationMs, MAX(qualityClass) AS bestQuality, MAX(releaseTypeTag) AS releaseTypeTag, " +
            "MIN(dateAdded) AS dateAdded FROM tracks WHERE isHidden = 0 GROUP BY albumKey",
    )
    suspend fun albumAggregates(): List<AlbumAggregate>

    @Query(
        "SELECT albumKey, artUri, artSource, artVersion FROM tracks WHERE isHidden = 0 AND artUri IS NOT NULL " +
            "ORDER BY albumKey, COALESCE(discNumber, 1), trackNumber IS NULL, trackNumber, fileNameSortKey",
    )
    suspend fun albumArtRows(): List<AlbumArtRow>

    @Query("DELETE FROM albums")
    suspend fun clearAlbums()

    @Upsert
    suspend fun upsertAlbums(albums: List<AlbumEntity>)

    @Query("DELETE FROM artists")
    suspend fun clearArtists()

    @Upsert
    suspend fun upsertArtists(artists: List<ArtistEntity>)

    @Transaction
    suspend fun replaceAlbumsAndArtists(albums: List<AlbumEntity>, artists: List<ArtistEntity>) {
        clearAlbums()
        clearArtists()
        albums.chunked(CHUNK).forEach { upsertAlbums(it) }
        artists.chunked(CHUNK).forEach { upsertArtists(it) }
    }

    private companion object {
        const val CHUNK = 500
    }
}
