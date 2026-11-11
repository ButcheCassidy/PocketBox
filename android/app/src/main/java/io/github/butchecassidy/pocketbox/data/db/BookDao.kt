// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Audiobook chapters, listening progress and bookmarks. A book is every chapter sharing an album key. */
@Dao
interface BookDao {
    /** Every chapter, book by book, in listening order. Short chapters are never hidden. */
    @Query(
        "SELECT $TRACK_COLUMNS FROM tracks WHERE isAudiobook = 1 " +
            "ORDER BY albumKey, COALESCE(discNumber, 1), trackNumber IS NULL, trackNumber, fileNameSortKey",
    )
    fun chapters(): Flow<List<TrackRow>>

    @Query("SELECT * FROM book_progress")
    fun progress(): Flow<List<BookProgressEntity>>

    /** Saves where listening stopped; skipped if the chapter left the library meanwhile. */
    @Query(
        "INSERT OR REPLACE INTO book_progress (bookKey, trackId, positionMs, updatedAt) " +
            "SELECT :bookKey, :trackId, :positionMs, :now WHERE EXISTS (SELECT 1 FROM tracks WHERE id = :trackId)",
    )
    suspend fun saveProgress(bookKey: String, trackId: Long, positionMs: Long, now: Long)

    @Query("SELECT * FROM bookmarks WHERE bookKey = :bookKey ORDER BY createdAt DESC")
    fun bookmarks(bookKey: String): Flow<List<BookmarkEntity>>

    /** Adds a bookmark; skipped if the chapter left the library meanwhile. */
    @Query(
        "INSERT INTO bookmarks (bookKey, trackId, positionMs, createdAt) " +
            "SELECT :bookKey, :trackId, :positionMs, :now WHERE EXISTS (SELECT 1 FROM tracks WHERE id = :trackId)",
    )
    suspend fun addBookmark(bookKey: String, trackId: Long, positionMs: Long, now: Long)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)
}
