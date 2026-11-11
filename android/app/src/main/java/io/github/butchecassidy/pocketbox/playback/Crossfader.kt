// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.playback

import android.content.Context
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Crossfade between songs (0–12 s). Near the end of a song, a short-lived helper player takes over
 * the outgoing song's tail and fades it out while the main player moves on and fades the next song in.
 * The helper shares the main player's audio session, so the equalizer applies to both, and it never
 * takes audio focus. With crossfade at 0 nothing here runs and playback stays gapless.
 */
class Crossfader(
    private val context: Context,
    private val player: () -> ExoPlayer,
    private val mixer: VolumeMixer,
    private val seconds: () -> Int,
    private val outgoingVolume: () -> Float,
    private val scope: CoroutineScope,
) : Player.Listener {
    private var message: PlayerMessage? = null
    private var helper: ExoPlayer? = null
    private var ramp: Job? = null
    private var ownTransition = false

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (ownTransition) {
            ownTransition = false
        } else {
            stop()
        }
        schedule()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying && !player().playWhenReady) stop()
    }

    override fun onRepeatModeChanged(repeatMode: Int) = schedule()

    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) schedule()
    }

    /** (Re)plans the crossfade for the current song. Called on every change that could affect it. */
    fun schedule() {
        message?.cancel()
        message = null
        val exo = player()
        val fadeMs = seconds() * 1000L
        val durationMs = exo.currentMediaItem?.mediaMetadata?.durationMs ?: return
        if (fadeMs <= 0 || exo.repeatMode == Player.REPEAT_MODE_ONE || !exo.hasNextMediaItem()) return
        if (durationMs < fadeMs * 2 + MIN_SOLO_MS) return
        message = exo.createMessage { _, _ -> start(fadeMs) }
            .setLooper(Looper.getMainLooper())
            .setPosition(exo.currentMediaItemIndex, durationMs - fadeMs)
            .setDeleteAfterDelivery(true)
            .send()
    }

    private fun start(fadeMs: Long) {
        val exo = player()
        val outgoing = exo.currentMediaItem ?: return
        if (!exo.isPlaying || !exo.hasNextMediaItem()) return
        stop()
        val startVolume = outgoingVolume()
        helper = PlayerFactory.create(context, bitPerfect = false, pauseOnDisconnect = false, exo.audioSessionId, isMain = false).apply {
            volume = startVolume
            setMediaItem(outgoing, exo.currentPosition)
            prepare()
            play()
        }
        mixer.crossfade = 0f
        ownTransition = true
        exo.seekToNextMediaItem()
        ramp = scope.launch {
            val steps = (fadeMs / STEP_MS).toInt().coerceAtLeast(1)
            for (step in 1..steps) {
                val t = step.toFloat() / steps
                helper?.volume = startVolume * (1f - t)
                mixer.crossfade = t
                delay(STEP_MS)
            }
            releaseHelper()
        }
    }

    /** Ends any crossfade immediately (pause, skip, queue change). */
    fun stop() {
        ramp?.cancel()
        ramp = null
        releaseHelper()
        mixer.crossfade = 1f
    }

    fun release() {
        message?.cancel()
        stop()
    }

    private fun releaseHelper() {
        helper?.release()
        helper = null
    }

    private companion object {
        const val STEP_MS = 50L
        const val MIN_SOLO_MS = 1_000L
    }
}
