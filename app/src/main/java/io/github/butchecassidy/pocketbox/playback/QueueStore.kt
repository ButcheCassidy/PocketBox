// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.github.butchecassidy.pocketbox.data.db.PlaybackDao
import io.github.butchecassidy.pocketbox.data.db.QueueItemEntity
import io.github.butchecassidy.pocketbox.data.db.QueueStateEntity
import io.github.butchecassidy.pocketbox.data.repo.LibraryRepository
import io.github.butchecassidy.pocketbox.domain.queue.ShuffleSequence
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** A queue read back from storage, ready to hand to the player. */
class RestoredQueue(
    val items: List<MediaItem>,
    val index: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
    val shuffle: ShuffleSequence?,
)

/** Saves and restores the queue, position, shuffle order and repeat mode across restarts. */
class QueueStore(
    private val dao: PlaybackDao,
    private val library: LibraryRepository,
    private val io: CoroutineDispatcher,
) {
    /** A snapshot taken on the main thread (players must only be read there), then written on [io]. */
    class Snapshot(
        val trackIds: List<Long>,
        val manual: List<Boolean>,
        val index: Int,
        val positionMs: Long,
        val shuffleEnabled: Boolean,
        val repeatMode: Int,
        val playOrder: List<Int>,
    )

    fun snapshot(player: Player): Snapshot {
        val count = player.mediaItemCount
        val ids = ArrayList<Long>(count)
        val manual = ArrayList<Boolean>(count)
        for (i in 0 until count) {
            val item = player.getMediaItemAt(i)
            ids += MediaItems.trackId(item.mediaId) ?: continue
            manual += MediaItems.isManual(item)
        }
        val order = ArrayList<Int>(count)
        val timeline = player.currentTimeline
        if (!timeline.isEmpty && ids.size == count) {
            var i = timeline.getFirstWindowIndex(true)
            while (i != C.INDEX_UNSET && order.size < count) {
                order += i
                i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true)
            }
        }
        return Snapshot(
            trackIds = ids,
            manual = manual,
            index = player.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = player.currentPosition.coerceAtLeast(0),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            playOrder = order,
        )
    }

    suspend fun save(snapshot: Snapshot) = withContext(io) {
        dao.saveQueue(
            snapshot.trackIds.mapIndexed { i, id -> QueueItemEntity(i, id, snapshot.manual[i]) },
            QueueStateEntity(
                currentIndex = snapshot.index,
                positionMs = snapshot.positionMs,
                shuffleEnabled = snapshot.shuffleEnabled,
                repeatMode = snapshot.repeatMode,
                shuffleOrder = snapshot.playOrder.joinToString(","),
            ),
        )
    }

    suspend fun savePosition(index: Int, positionMs: Long) = withContext(io) { dao.savePosition(index, positionMs) }

    /** Reads the saved queue. Songs deleted since are dropped and the position adjusted. */
    suspend fun restore(): RestoredQueue? = withContext(io) {
        val state = dao.queueState() ?: return@withContext null
        val saved = dao.queueItems()
        if (saved.isEmpty()) return@withContext null
        val tracks = library.tracksByIds(saved.map { it.trackId }).associateBy { it.id }
        val kept = saved.filter { it.trackId in tracks }
        if (kept.isEmpty()) return@withContext null
        val items = kept.map { MediaItems.playable(tracks.getValue(it.trackId), it.isManual) }
        val currentSaved = saved.getOrNull(state.currentIndex)
        val index = kept.indexOfFirst { it.position == currentSaved?.position }
        val order = state.shuffleOrder.split(',').mapNotNull { it.toIntOrNull() }
        RestoredQueue(
            items = items,
            index = index.coerceAtLeast(0),
            positionMs = if (index >= 0) state.positionMs else 0,
            shuffleEnabled = state.shuffleEnabled,
            repeatMode = state.repeatMode,
            // A saved shuffle order only still fits if no songs disappeared.
            shuffle = if (kept.size == saved.size) ShuffleSequence.restore(order, kept.size) else null,
        )
    }
}
