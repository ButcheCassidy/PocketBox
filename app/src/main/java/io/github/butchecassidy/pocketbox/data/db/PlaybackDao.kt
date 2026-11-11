// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Saved queue and equalizer presets. */
@Dao
interface PlaybackDao {
    @Query("SELECT * FROM queue_items ORDER BY position")
    suspend fun queueItems(): List<QueueItemEntity>

    @Query("SELECT * FROM queue_state WHERE id = 0")
    suspend fun queueState(): QueueStateEntity?

    /** The saved song that was playing, without reading the whole queue (used by the widget). */
    @Query("SELECT trackId FROM queue_items WHERE position = (SELECT currentIndex FROM queue_state WHERE id = 0)")
    suspend fun savedCurrentTrackId(): Long?

    @Query("DELETE FROM queue_items")
    suspend fun clearQueueItems()

    @Insert
    suspend fun insertQueueItems(items: List<QueueItemEntity>)

    @Upsert
    suspend fun upsertQueueState(state: QueueStateEntity)

    /** Replaces the whole saved queue in one transaction. */
    @Transaction
    suspend fun saveQueue(items: List<QueueItemEntity>, state: QueueStateEntity) {
        clearQueueItems()
        items.chunked(CHUNK).forEach { insertQueueItems(it) }
        upsertQueueState(state)
    }

    @Query("SELECT * FROM eq_presets ORDER BY name COLLATE NOCASE")
    fun eqPresets(): Flow<List<EqPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEqPreset(preset: EqPresetEntity)

    @Query("DELETE FROM eq_presets WHERE id = :id")
    suspend fun deleteEqPreset(id: Long)

    private companion object {
        const val CHUNK = 500
    }
}
