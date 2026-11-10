// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.butchecassidy.pocketbox.domain.model.ArtRef
import io.github.butchecassidy.pocketbox.domain.model.RepeatMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One song in the queue as the UI shows it. */
data class QueueEntry(
    val index: Int,
    val trackId: Long,
    val title: String,
    val artist: String,
    val art: ArtRef?,
    val durationMs: Long,
    val isManual: Boolean,
)

/** The queue in list order, plus the order songs will actually play in (differs when shuffled). */
data class QueueState(val entries: List<QueueEntry> = emptyList(), val playOrder: List<Int> = emptyList()) {
    val hasManualEntries: Boolean get() = entries.any { it.isManual }
}

/** Everything the UI needs about playback, except the position, which is read once per frame. */
data class PlayerState(
    val connected: Boolean = false,
    val currentIndex: Int = C.INDEX_UNSET,
    val currentTrackId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val art: ArtRef? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val speed: Float = 1f,
    val pitch: Float = 1f,
) {
    val hasMedia: Boolean get() = currentTrackId != null
}

/** A copy of the queue taken before a change, so the change can be undone. */
class QueueSnapshot(val items: List<MediaItem>, val index: Int, val positionMs: Long, val shuffle: Boolean)

/**
 * Pocket Box's UI connection to [PlaybackService], through the official Media3 [MediaController].
 * Exposes state as [StateFlow]s and offers simple commands. Reconnects automatically if the
 * service was stopped. Must be used from the main thread, like every Media3 controller.
 */
class PlayerConnection(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(PlayerState())
    private val _queue = MutableStateFlow(QueueState())
    private var controller: CompletableDeferred<MediaController>? = null

    val state: StateFlow<PlayerState> = _state.asStateFlow()
    val queue: StateFlow<QueueState> = _queue.asStateFlow()

    /** Current playback position; call from a frame callback only while a progress bar is visible. */
    fun positionMs(): Long {
        val c = connectedOrNull() ?: return 0
        return c.currentPosition.coerceAtLeast(0)
    }

    /** Starts connecting (restores the last queue in the service) without sending any command. */
    fun connect() {
        scope.launch { controller() }
    }

    private fun connectedOrNull(): MediaController? =
        controller?.takeIf { it.isCompleted }?.getCompleted()?.takeIf { it.isConnected }

    private suspend fun controller(): MediaController {
        controller?.let { existing ->
            val c = existing.await()
            if (c.isConnected) return c
        }
        val deferred = CompletableDeferred<MediaController>()
        controller = deferred
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    _state.value = _state.value.copy(connected = false, isPlaying = false)
                }
            })
            .buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull()
            if (c == null) {
                controller = null
                deferred.completeExceptionally(IllegalStateException("Playback service unavailable"))
                return@addListener
            }
            c.addListener(listener)
            update(c, queueChanged = true)
            deferred.complete(c)
        }, ContextCompat.getMainExecutor(context))
        return deferred.await()
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val queueChanged = events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            )
            update(player, queueChanged)
        }
    }

    private fun update(player: Player, queueChanged: Boolean) {
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        _state.value = PlayerState(
            connected = true,
            currentIndex = player.currentMediaItemIndex,
            currentTrackId = MediaItems.trackId(item?.mediaId),
            title = metadata?.title?.toString().orEmpty(),
            artist = metadata?.artist?.toString().orEmpty(),
            album = metadata?.albumTitle?.toString().orEmpty(),
            art = metadata?.let(MediaItems::artRef),
            isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: metadata?.durationMs ?: 0,
            shuffle = player.shuffleModeEnabled,
            repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            },
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            speed = player.playbackParameters.speed,
            pitch = player.playbackParameters.pitch,
        )
        if (queueChanged) _queue.value = readQueue(player)
    }

    private fun readQueue(player: Player): QueueState {
        val count = player.mediaItemCount
        val entries = ArrayList<QueueEntry>(count)
        for (i in 0 until count) {
            val item = player.getMediaItemAt(i)
            val metadata = item.mediaMetadata
            entries += QueueEntry(
                index = i,
                trackId = MediaItems.trackId(item.mediaId) ?: -1,
                title = metadata.title?.toString().orEmpty(),
                artist = metadata.artist?.toString().orEmpty(),
                art = MediaItems.artRef(metadata),
                durationMs = metadata.durationMs ?: 0,
                isManual = MediaItems.isManual(item),
            )
        }
        val order = ArrayList<Int>(count)
        val timeline = player.currentTimeline
        var i = timeline.getFirstWindowIndex(player.shuffleModeEnabled)
        while (i != C.INDEX_UNSET && order.size < count) {
            order += i
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        }
        return QueueState(entries, order)
    }

    private fun command(block: (MediaController) -> Unit) {
        scope.launch { runCatching { controller() }.getOrNull()?.let(block) }
    }

    // ---- Transport ----

    fun togglePlay() = command { c ->
        if (c.isPlaying || (c.playWhenReady && c.playbackState == Player.STATE_BUFFERING)) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() = command { it.seekToNext() }
    fun previous() = command { it.seekToPrevious() }
    fun seekTo(positionMs: Long) = command { it.seekTo(positionMs.coerceAtLeast(0)) }
    fun skipTo(index: Int) = command { if (index in 0 until it.mediaItemCount) it.seekToDefaultPosition(index) }
    fun setShuffle(enabled: Boolean) = command { it.shuffleModeEnabled = enabled }

    /** Off → all → one → off. */
    fun cycleRepeat() = command {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeedAndPitch(speed: Float, pitch: Float) = command { it.playbackParameters = PlaybackParameters(speed, pitch) }

    // ---- Queue ----

    fun snapshot(): QueueSnapshot? {
        val c = connectedOrNull() ?: return null
        return QueueSnapshot(
            items = (0 until c.mediaItemCount).map { i ->
                val item = c.getMediaItemAt(i)
                MediaItems.request(MediaItems.trackId(item.mediaId) ?: -1, MediaItems.isManual(item))
            },
            index = c.currentMediaItemIndex,
            positionMs = c.currentPosition,
            shuffle = c.shuffleModeEnabled,
        )
    }

    fun restore(snapshot: QueueSnapshot) = command { c ->
        c.setMediaItems(snapshot.items, snapshot.index.coerceAtLeast(0), snapshot.positionMs)
        c.shuffleModeEnabled = snapshot.shuffle
        c.prepare()
    }

    /** Replaces the queue and starts playing at [startIndex]. */
    fun setQueue(trackIds: List<Long>, startIndex: Int, shuffle: Boolean) = command { c ->
        if (trackIds.isEmpty()) return@command
        c.setMediaItems(trackIds.map { MediaItems.request(it) }, startIndex.coerceIn(0, trackIds.lastIndex), 0)
        c.shuffleModeEnabled = shuffle
        c.prepare()
        c.play()
    }

    /** "Play next": right after the current song (and, when shuffled, next in play order too). */
    fun playNext(trackIds: List<Long>) = command { c ->
        val items = trackIds.map { MediaItems.request(it, manual = true) }
        if (c.mediaItemCount == 0) {
            c.setMediaItems(items)
            c.prepare()
        } else {
            c.addMediaItems(c.currentMediaItemIndex + 1, items)
        }
    }

    fun addToQueue(trackIds: List<Long>) = command { c ->
        val items = trackIds.map { MediaItems.request(it, manual = true) }
        if (c.mediaItemCount == 0) {
            c.setMediaItems(items)
            c.prepare()
        } else {
            c.addMediaItems(items)
        }
    }

    fun removeAt(index: Int) = command { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) }

    /** Drag-to-reorder. With shuffle off, positions are list positions; with shuffle on, play-order positions. */
    fun move(fromPosition: Int, toPosition: Int) = command { c ->
        if (c.shuffleModeEnabled) {
            val args = Bundle().apply {
                putInt(PlaybackCommands.ARG_FROM, fromPosition)
                putInt(PlaybackCommands.ARG_TO, toPosition)
            }
            c.sendCustomCommand(PlaybackCommands.moveInPlayOrder, args)
        } else {
            c.moveMediaItem(fromPosition, toPosition)
        }
    }

    fun clear() = command { it.clearMediaItems() }

    // ---- Sleep timer ----

    fun setSleepTimer(minutes: Int, endOfTrack: Boolean, fadeOut: Boolean) = command { c ->
        val args = Bundle().apply {
            putInt(PlaybackCommands.ARG_MINUTES, minutes)
            putBoolean(PlaybackCommands.ARG_END_OF_TRACK, endOfTrack)
            putBoolean(PlaybackCommands.ARG_FADE, fadeOut)
        }
        c.sendCustomCommand(PlaybackCommands.sleepSet, args)
    }

    fun cancelSleepTimer() = command { it.sendCustomCommand(PlaybackCommands.sleepCancel, Bundle.EMPTY) }
}
