// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps an audiobook's place: while a chapter plays, the spot is saved every few seconds, and again
 * on pause, seek, chapter change and when the service stops. Songs are ignored.
 */
class BookProgressTracker(
    private val player: () -> Player,
    private val scope: CoroutineScope,
    /** Outlives the service, so the last save still lands while the service shuts down. */
    private val saveScope: CoroutineScope,
    private val save: suspend (bookKey: String, trackId: Long, positionMs: Long) -> Unit,
) : Player.Listener {
    private var ticker: Job? = null

    // Saves land in the order they were taken, so an older spot never overwrites a newer one.
    private val saveOrder = Mutex()

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        saveNow()
        updateTicker()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (mediaItem != null) saveNow()
        updateTicker()
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) saveNow()
        // The chapter left the queue (say, an album was played instead): keep the spot it had.
        if (reason == Player.DISCONTINUITY_REASON_REMOVE) save(oldPosition.mediaItem, oldPosition.positionMs)
    }

    /** Saves the current chapter's spot right away, if a chapter is loaded. Main thread only. */
    fun saveNow() {
        val exo = player()
        save(exo.currentMediaItem, exo.currentPosition)
    }

    private fun save(item: MediaItem?, positionMs: Long) {
        val bookKey = MediaItems.bookKey(item) ?: return
        val trackId = MediaItems.trackId(item?.mediaId) ?: return
        saveScope.launch { saveOrder.withLock { save(bookKey, trackId, positionMs) } }
    }

    /** Ticks only while a book chapter is actually playing. */
    private fun updateTicker() {
        val exo = player()
        if (!exo.isPlaying || MediaItems.bookKey(exo.currentMediaItem) == null) {
            stop()
        } else if (ticker?.isActive != true) {
            ticker = scope.launch {
                while (isActive) {
                    delay(SAVE_EVERY_MS)
                    saveNow()
                }
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
    }

    private companion object {
        const val SAVE_EVERY_MS = 5_000L
    }
}
