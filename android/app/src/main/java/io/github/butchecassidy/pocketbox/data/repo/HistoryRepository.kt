// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.repo

import io.github.butchecassidy.pocketbox.data.db.PocketBoxDatabase
import io.github.butchecassidy.pocketbox.data.db.RecentSearchEntity
import io.github.butchecassidy.pocketbox.data.prefs.SettingsRepository
import io.github.butchecassidy.pocketbox.domain.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Listening history and recent searches. Both stay on the device and can be wiped in one tap. */
class HistoryRepository(
    db: PocketBoxDatabase,
    private val settings: SettingsRepository,
    private val io: CoroutineDispatcher,
) {
    private val dao = db.userDataDao()

    fun recentlyPlayedTracks(limit: Int = LIST_SIZE): Flow<List<Track>> =
        dao.recentlyPlayedTracks(limit).map { rows -> rows.map { it.toTrack() } }.flowOn(io)

    fun mostPlayedTracks(limit: Int = LIST_SIZE): Flow<List<Track>> =
        dao.mostPlayedTracks(limit).map { rows -> rows.map { it.toTrack() } }.flowOn(io)

    /**
     * Records one counted play (30 s or half the track, decided by the player). The day is always
     * noted for the donation reminder's "played on 7 days" rule, even when history is paused,
     * because it stores only a date, never what was played.
     */
    suspend fun recordPlay(trackId: Long) = withContext(io) {
        settings.recordPlayDay(LocalDate.now().toEpochDay())
        if (!settings.current().historyPaused) dao.recordPlay(trackId, System.currentTimeMillis())
    }

    suspend fun clearHistory() = withContext(io) { dao.clearHistory() }

    val recentSearches: Flow<List<String>> = dao.recentSearches(RECENT_SEARCHES)

    suspend fun addRecentSearch(query: String) = withContext(io) {
        val clean = query.trim().take(MAX_QUERY_LENGTH)
        if (clean.isEmpty()) return@withContext
        dao.upsertRecentSearch(RecentSearchEntity(clean, System.currentTimeMillis()))
        dao.trimRecentSearches(RECENT_SEARCHES)
    }

    suspend fun clearRecentSearches() = withContext(io) { dao.clearRecentSearches() }

    private companion object {
        const val LIST_SIZE = 100
        const val RECENT_SEARCHES = 10
        const val MAX_QUERY_LENGTH = 100
    }
}
