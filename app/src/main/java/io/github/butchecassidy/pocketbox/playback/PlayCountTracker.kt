// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.playback

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Counts a play once a song has actually been listened to for 30 seconds or half its length,
 * whichever comes first. Uses one timer per listening stretch (no polling); pauses stop the clock.
 */
class PlayCountTracker(
    private val player: () -> Player,
    private val scope: CoroutineScope,
    private val onCounted: suspend (trackId: Long) -> Unit,
) : Player.Listener {
    private var trackId: Long? = null
    private var thresholdMs = MAX_THRESHOLD_MS
    private var listenedMs = 0L
    private var playingSince = 0L
    private var counted = false
    private var timer: Job? = null

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        stopClock()
        trackId = MediaItems.trackId(mediaItem?.mediaId)
        val duration = mediaItem?.mediaMetadata?.durationMs ?: 0L
        thresholdMs = if (duration > 0) minOf(MAX_THRESHOLD_MS, duration / 2) else MAX_THRESHOLD_MS
        listenedMs = 0
        counted = false
        if (player().isPlaying) startClock()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) startClock() else stopClock()
    }

    private fun startClock() {
        val id = trackId ?: return
        if (counted || timer?.isActive == true) return
        playingSince = SystemClock.elapsedRealtime()
        val speed = player().playbackParameters.speed.coerceAtLeast(MIN_SPEED)
        val remaining = ((thresholdMs - listenedMs) / speed).toLong().coerceAtLeast(0)
        timer = scope.launch {
            delay(remaining)
            counted = true
            onCounted(id)
        }
    }

    private fun stopClock() {
        val running = timer ?: return
        running.cancel()
        timer = null
        if (!counted) {
            val speed = player().playbackParameters.speed.coerceAtLeast(MIN_SPEED)
            listenedMs += ((SystemClock.elapsedRealtime() - playingSince) * speed).toLong()
        }
    }

    private companion object {
        const val MAX_THRESHOLD_MS = 30_000L
        const val MIN_SPEED = 0.1f
    }
}
