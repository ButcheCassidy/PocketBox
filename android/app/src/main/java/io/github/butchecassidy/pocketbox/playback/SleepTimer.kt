// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.playback

import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the sleep timer is set to. [endsAtMs] is wall-clock time for display. */
sealed interface SleepTimerState {
    data object Off : SleepTimerState
    data class Timed(val endsAtMs: Long, val fadeOut: Boolean) : SleepTimerState
    data class EndOfTrack(val fadeOut: Boolean) : SleepTimerState
}

/**
 * Pauses after a number of minutes or at the end of the current song, optionally fading out over
 * the last [FADE_MS]. End-of-song uses ExoPlayer's own "pause at end of item", so it's exact.
 */
class SleepTimer(
    private val player: () -> ExoPlayer,
    private val mixer: VolumeMixer,
    private val scope: CoroutineScope,
) : Player.Listener {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()
    private var job: Job? = null
    private var fadeMessage: PlayerMessage? = null

    fun setMinutes(minutes: Int, fadeOut: Boolean) {
        cancel()
        val totalMs = minutes.coerceIn(1, MAX_MINUTES) * 60_000L
        _state.value = SleepTimerState.Timed(System.currentTimeMillis() + totalMs, fadeOut)
        // Measured on the clock that keeps running in deep sleep, so a paused, sleeping phone stays on time.
        val fireAt = SystemClock.elapsedRealtime() + if (fadeOut) (totalMs - FADE_MS).coerceAtLeast(0) else totalMs
        job = scope.launch {
            while (true) {
                val left = fireAt - SystemClock.elapsedRealtime()
                if (left <= 0) break
                delay(left)
            }
            if (fadeOut) fade()
            finish()
        }
    }

    fun setEndOfTrack(fadeOut: Boolean) {
        cancel()
        val exo = player()
        if (exo.mediaItemCount == 0) return
        _state.value = SleepTimerState.EndOfTrack(fadeOut)
        exo.pauseAtEndOfMediaItems = true
        val duration = exo.duration.takeIf { it != C.TIME_UNSET } ?: exo.currentMediaItem?.mediaMetadata?.durationMs ?: C.TIME_UNSET
        if (fadeOut && duration != C.TIME_UNSET && duration > FADE_MS) {
            fadeMessage = exo.createMessage { _, _ -> job = scope.launch { fade() } }
                .setLooper(Looper.getMainLooper())
                .setPosition(exo.currentMediaItemIndex, duration - FADE_MS)
                .setDeleteAfterDelivery(true)
                .send()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        fadeMessage?.cancel()
        fadeMessage = null
        player().pauseAtEndOfMediaItems = false
        mixer.sleepFade = 1f
        _state.value = SleepTimerState.Off
    }

    /** End-of-song mode follows a skip: the new song becomes the last one, with its own fade. */
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val endOfTrack = _state.value as? SleepTimerState.EndOfTrack ?: return
        setEndOfTrack(endOfTrack.fadeOut)
    }

    /** End-of-song mode: ExoPlayer paused at the end of the item, so the timer is done. */
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
            _state.value is SleepTimerState.EndOfTrack
        ) {
            cancel()
        }
    }

    private suspend fun fade() {
        val steps = (FADE_MS / FADE_STEP_MS).toInt()
        for (step in 1..steps) {
            mixer.sleepFade = 1f - step.toFloat() / steps
            delay(FADE_STEP_MS)
        }
    }

    private fun finish() {
        player().pause()
        cancel()
    }

    companion object {
        const val FADE_MS = 10_000L
        private const val FADE_STEP_MS = 50L
        private const val MAX_MINUTES = 24 * 60
    }
}
