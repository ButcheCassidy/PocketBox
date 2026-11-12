// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Everything the user creates: playlists, favourites, history, recent searches and release overrides. */
@Dao
interface UserDataDao {
    // ---- Lyrics timing ----

    @Query("SELECT offsetMs FROM lyric_offsets WHERE trackId = :trackId")
    fun lyricOffset(trackId: Long): Flow<Long?>

    @Query("SELECT offsetMs FROM lyric_offsets WHERE trackId = :trackId")
    suspend fun lyricOffsetOnce(trackId: Long): Long?

    /** Saves a song's lyrics timing; skipped if the song left the library meanwhile. */
    @Query(
        "INSERT OR REPLACE INTO lyric_offsets (trackId, offsetMs) " +
            "SELECT :trackId, :offsetMs WHERE EXISTS (SELECT 1 FROM tracks WHERE id = :trackId)",
    )
    suspend fun setLyricOffset(trackId: Long, offsetMs: Long)

    @Query("DELETE FROM lyric_offsets WHERE trackId = :trackId")
    suspend fun clearLyricOffset(trackId: Long)

    // ---- Playlists ----

    @Query(
        "SELECT p.id, p.name, COUNT(e.id) AS trackCount, COALESCE(SUM(t.durationMs), 0) AS durationMs, " +
            "(SELECT t2.artUri FROM playlist_entries e2 JOIN tracks t2 ON t2.id = e2.trackId " +
            "WHERE e2.playlistId = p.id AND t2.artUri IS NOT NULL ORDER BY e2.position LIMIT 1) AS artUri, " +
            "(SELECT t2.artSource FROM playlist_entries e2 JOIN tracks t2 ON t2.id = e2.trackId " +
            "WHERE e2.playlistId = p.id AND t2.artUri IS NOT NULL ORDER BY e2.position LIMIT 1) AS artSource, " +
            "(SELECT t2.artVersion FROM playlist_entries e2 JOIN tracks t2 ON t2.id = e2.trackId " +
            "WHERE e2.playlistId = p.id AND t2.artUri IS NOT NULL ORDER BY e2.position LIMIT 1) AS artVersion " +
            "FROM playlists p LEFT JOIN playlist_entries e ON e.playlistId = p.id " +
            "LEFT JOIN tracks t ON t.id = e.trackId GROUP BY p.id ORDER BY p.name COLLATE NOCASE",
    )
    fun playlists(): Flow<List<PlaylistSummaryRow>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun playlist(id: Long): Flow<PlaylistEntity?>

    @Query(
        "SELECT $TRACK_COLUMNS FROM playlist_entries e JOIN tracks ON tracks.id = e.trackId " +
            "WHERE e.playlistId = :playlistId AND tracks.isHidden = 0 AND tracks.isAudiobook = 0 ORDER BY e.position",
    )
    fun playlistTracks(playlistId: Long): Flow<List<TrackRow>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun lastPosition(playlistId: Long): Int

    @Insert
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun clearEntries(playlistId: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touchPlaylist(id: Long, now: Long)

    @Query("SELECT id FROM tracks WHERE id IN (:ids)")
    suspend fun existingTrackIds(ids: List<Long>): List<Long>

    /** Drops songs a rescan removed in the meantime; adding them would break the link to the tracks table. */
    suspend fun stillInLibrary(trackIds: List<Long>): List<Long> {
        val present = trackIds.distinct().chunked(CHUNK).flatMapTo(HashSet()) { existingTrackIds(it) }
        return trackIds.filter { it in present }
    }

    @Transaction
    suspend fun appendToPlaylist(playlistId: Long, trackIds: List<Long>, now: Long) {
        val start = lastPosition(playlistId) + 1
        insertEntries(stillInLibrary(trackIds).mapIndexed { i, id -> PlaylistEntryEntity(playlistId = playlistId, trackId = id, position = start + i) })
        touchPlaylist(playlistId, now)
    }

    /** Rewrites the whole order; used for drag-to-reorder and removing entries. */
    @Transaction
    suspend fun replaceEntries(playlistId: Long, trackIds: List<Long>, now: Long) {
        clearEntries(playlistId)
        insertEntries(stillInLibrary(trackIds).mapIndexed { i, id -> PlaylistEntryEntity(playlistId = playlistId, trackId = id, position = i) })
        touchPlaylist(playlistId, now)
    }

    // ---- Favourites ----

    @Query("SELECT trackId FROM favorites")
    fun favoriteIds(): Flow<List<Long>>

    @Query(
        "SELECT $TRACK_COLUMNS FROM favorites f JOIN tracks ON tracks.id = f.trackId " +
            "WHERE tracks.isHidden = 0 AND tracks.isAudiobook = 0 ORDER BY f.addedAt DESC",
    )
    fun favoriteTracks(): Flow<List<TrackRow>>

    // Only for a song still in the library (a rescan may have just removed it).
    @Query("INSERT OR IGNORE INTO favorites (trackId, addedAt) SELECT :trackId, :now WHERE EXISTS (SELECT 1 FROM tracks WHERE id = :trackId)")
    suspend fun addFavorite(trackId: Long, now: Long)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun removeFavorite(trackId: Long)

    // ---- Listening history ----

    @Query(
        // Counted only while the song is still in the library: it may have been deleted while it played.
        "INSERT INTO play_stats (trackId, playCount, lastPlayedAt) SELECT :trackId, 1, :now " +
            "WHERE EXISTS (SELECT 1 FROM tracks WHERE id = :trackId) " +
            "ON CONFLICT(trackId) DO UPDATE SET playCount = playCount + 1, lastPlayedAt = :now",
    )
    suspend fun recordPlay(trackId: Long, now: Long)

    @Query(
        "SELECT $TRACK_COLUMNS FROM play_stats s JOIN tracks ON tracks.id = s.trackId " +
            "WHERE tracks.isHidden = 0 AND tracks.isAudiobook = 0 ORDER BY s.lastPlayedAt DESC LIMIT :limit",
    )
    fun recentlyPlayedTracks(limit: Int): Flow<List<TrackRow>>

    @Query(
        "SELECT $TRACK_COLUMNS FROM play_stats s JOIN tracks ON tracks.id = s.trackId " +
            "WHERE tracks.isHidden = 0 AND tracks.isAudiobook = 0 ORDER BY s.playCount DESC, s.lastPlayedAt DESC LIMIT :limit",
    )
    fun mostPlayedTracks(limit: Int): Flow<List<TrackRow>>

    @Query("DELETE FROM play_stats")
    suspend fun clearHistory()

    // ---- Recent searches ----

    @Query("SELECT `query` FROM recent_searches ORDER BY searchedAt DESC LIMIT :limit")
    fun recentSearches(limit: Int): Flow<List<String>>

    @Upsert
    suspend fun upsertRecentSearch(search: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE searchedAt < (SELECT MIN(searchedAt) FROM (SELECT searchedAt FROM recent_searches ORDER BY searchedAt DESC LIMIT :keep))")
    suspend fun trimRecentSearches(keep: Int)

    @Query("DELETE FROM recent_searches")
    suspend fun clearRecentSearches()

    // ---- Release type overrides ----

    @Upsert
    suspend fun setReleaseOverride(override: ReleaseOverrideEntity)

    @Query("DELETE FROM release_overrides WHERE albumKey = :albumKey")
    suspend fun clearReleaseOverride(albumKey: String)

    private companion object {
        const val CHUNK = 500
    }
}
