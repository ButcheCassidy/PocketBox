// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.github.butchecassidy.pocketbox.MainActivity
import io.github.butchecassidy.pocketbox.PocketBoxApp
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.di.AppContainer
import io.github.butchecassidy.pocketbox.domain.audio.ReplayGainCalculator
import io.github.butchecassidy.pocketbox.domain.model.UserSettings
import io.github.butchecassidy.pocketbox.domain.queue.ShuffleSequence
import io.github.butchecassidy.pocketbox.widget.WidgetStatePublisher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The one place music plays. Exposed to the notification, lock screen, Quick Settings, Bluetooth
 * devices and Pocket Box's own UI through a Media3 [MediaLibraryService.MediaLibrarySession].
 * Third-party apps that aren't trusted by Android may only use basic transport controls and can't
 * see the queue or browse the library.
 */
class PlaybackService : MediaLibraryService() {
    private lateinit var container: AppContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var exo: ExoPlayer
    private var session: MediaLibrarySession? = null
    private lateinit var browseTree: LibraryBrowseTree
    private lateinit var sleepTimer: SleepTimer
    private lateinit var crossfader: Crossfader
    private lateinit var playCounter: PlayCountTracker
    private lateinit var effects: AudioEffectsController
    private lateinit var widgetPublisher: WidgetStatePublisher
    private val mixer = VolumeMixer { volume -> if (::exo.isInitialized) exo.volume = volume }
    private var settings = UserSettings()
    private var audioSessionId = C.AUDIO_SESSION_ID_UNSET
    private var saveJob: Job? = null
    private var errorsInARow = 0
    private var pausedByDisconnect = false

    override fun onCreate() {
        super.onCreate()
        container = (application as PocketBoxApp).container
        val audioManager = getSystemService(AudioManager::class.java)
        audioSessionId = audioManager.generateAudioSessionId()
        browseTree = LibraryBrowseTree(container.library, container.playlists, container.history)
        exo = createPlayer(bitPerfect = false)
        sleepTimer = SleepTimer({ exo }, mixer, scope)
        crossfader = Crossfader(this, { exo }, mixer, { if (settings.bitPerfect) 0 else settings.crossfadeSeconds }, { mixer.replayGain }, scope)
        playCounter = PlayCountTracker({ exo }, scope) { trackId -> container.history.recordPlay(trackId) }
        effects = AudioEffectsController(audioSessionId)
        widgetPublisher = WidgetStatePublisher(container.widgetState)
        attachListeners(exo)

        val sessionIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, PocketPlayer(exo), Callback())
            .setSessionActivity(sessionIntent)
            .setBitmapLoader(CacheBitmapLoader(ArtworkBitmapLoader(container.artwork, scope)))
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_music_note) },
        )
        audioManager.registerAudioDeviceCallback(bluetoothReconnect, null)

        scope.launch { restoreQueue() }
        scope.launch { container.settings.settings.collectLatest(::applySettings) }
        scope.launch { sleepTimer.state.collectLatest { container.sleepTimerState.value = it } }
        scope.launch { container.outputMonitor.reopenRequests.collect { reopenAudioOutput() } }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveQueueNow()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        saveQueueNow()
        getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(bluetoothReconnect)
        crossfader.release()
        effects.release()
        session?.release()
        session = null
        exo.release()
        container.sleepTimerState.value = SleepTimerState.Off
        widgetPublisher.onServiceStopped()
        scope.cancel()
        super.onDestroy()
    }

    private fun createPlayer(bitPerfect: Boolean): ExoPlayer =
        PlayerFactory.create(this, bitPerfect, settings.pauseOnDisconnect, audioSessionId, isMain = true).apply {
            setShuffleOrder(QueueShuffleOrder.startingAt(0, null))
        }

    private fun attachListeners(player: ExoPlayer) {
        player.addListener(serviceListener)
        player.addListener(sleepTimer)
        player.addListener(crossfader)
        player.addListener(playCounter)
        player.addListener(widgetPublisher)
        player.addAnalyticsListener(container.outputMonitor.analyticsListener)
    }

    private suspend fun restoreQueue() {
        val restored = container.queueStore.restore() ?: return
        if (exo.mediaItemCount > 0) return // a controller already started something
        exo.setMediaItems(restored.items, restored.index, restored.positionMs)
        restored.shuffle?.let { exo.setShuffleOrder(QueueShuffleOrder(it)) }
        exo.shuffleModeEnabled = restored.shuffleEnabled
        exo.repeatMode = restored.repeatMode
        exo.prepare()
    }

    /** Applies settings that affect playback. Switching bit-perfect mode rebuilds the player. */
    private fun applySettings(new: UserSettings) {
        val old = settings
        settings = new
        if (new.bitPerfect != old.bitPerfect) rebuildPlayer(new.bitPerfect)
        exo.setHandleAudioBecomingNoisy(new.pauseOnDisconnect)
        exo.playbackParameters = if (new.bitPerfect) {
            PlaybackParameters.DEFAULT
        } else {
            PlaybackParameters(new.playbackSpeed, new.playbackPitch)
        }
        updateReplayGain()
        effects.apply(new)
        container.outputMonitor.setBitPerfectRequested(new.bitPerfect)
        crossfader.schedule()
    }

    private fun updateReplayGain() {
        val trackId = MediaItems.trackId(exo.currentMediaItem?.mediaId)
        if (trackId == null || settings.bitPerfect) {
            mixer.replayGain = 1f
            return
        }
        scope.launch {
            val track = container.library.tracksByIds(listOf(trackId)).firstOrNull() ?: return@launch
            if (MediaItems.trackId(exo.currentMediaItem?.mediaId) == trackId) {
                mixer.replayGain = ReplayGainCalculator.volumeFactor(track.replayGain, settings.replayGainMode, settings.replayGainPreampDb)
            }
        }
    }

    /** Re-opens the audio output in place (same song, same position) so a new bit-perfect format applies. */
    private fun reopenAudioOutput() {
        if (exo.mediaItemCount == 0) return
        val playWhenReady = exo.playWhenReady
        exo.stop()
        exo.prepare()
        exo.playWhenReady = playWhenReady
    }

    /** Moves everything to a new player (bit-perfect needs a different audio output setup). */
    private fun rebuildPlayer(bitPerfect: Boolean) {
        val old = exo
        crossfader.stop()
        val items = (0 until old.mediaItemCount).map(old::getMediaItemAt)
        val order = (old.shuffleOrder as? QueueShuffleOrder)?.sequence
        val replacement = createPlayer(bitPerfect)
        if (items.isNotEmpty()) {
            replacement.setMediaItems(items, old.currentMediaItemIndex, old.currentPosition)
            order?.takeIf { it.size == items.size }?.let { replacement.setShuffleOrder(QueueShuffleOrder(it)) }
        }
        replacement.shuffleModeEnabled = old.shuffleModeEnabled
        replacement.repeatMode = old.repeatMode
        replacement.playWhenReady = old.playWhenReady
        replacement.volume = old.volume
        if (items.isNotEmpty()) replacement.prepare()
        exo = replacement
        attachListeners(replacement)
        session?.player = PocketPlayer(replacement)
        old.release()
    }

    private val serviceListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateReplayGain()
            saveQueueSoon()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) saveQueueSoon()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = saveQueueSoon()
        override fun onRepeatModeChanged(repeatMode: Int) = saveQueueSoon()

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) errorsInARow = 0 else saveQueueSoon()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            pausedByDisconnect = !playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
        }

        /** A file that was deleted, moved or whose folder access was revoked: skip it, never crash. */
        override fun onPlayerError(error: PlaybackException) {
            val title = exo.currentMediaItem?.mediaMetadata?.title ?: "This song"
            container.playbackMessages.tryEmit("Couldn't play \"$title\". It may have been moved or deleted.")
            container.appScope.launch { container.scanner.refreshAccess() }
            if (++errorsInARow <= MAX_ERRORS_IN_A_ROW && exo.hasNextMediaItem()) {
                exo.seekToNextMediaItem()
                exo.prepare()
            }
        }
    }

    /** Optional: resume when Bluetooth headphones reconnect, but only if their disconnect paused us. */
    private val bluetoothReconnect = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val bluetooth = addedDevices.any { it.isSink && it.type in OutputMonitor.bluetoothTypes }
            if (bluetooth && settings.resumeOnBluetooth && pausedByDisconnect && exo.mediaItemCount > 0) {
                pausedByDisconnect = false
                exo.play()
            }
        }
    }

    private fun saveQueueSoon() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            container.queueStore.save(container.queueStore.snapshot(exo))
        }
    }

    private fun saveQueueNow() {
        saveJob?.cancel()
        if (!::exo.isInitialized) return
        val snapshot = container.queueStore.snapshot(exo)
        container.appScope.launch { container.queueStore.save(snapshot) }
    }

    /** Intercepts shuffle so turning it on puts the playing song first and plays every other song once. */
    private inner class PocketPlayer(private val player: ExoPlayer) : ForwardingPlayer(player) {
        override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled && !player.shuffleModeEnabled) {
                val count = player.mediaItemCount
                player.setShuffleOrder(QueueShuffleOrder.startingAt(count, player.currentMediaItemIndex.takeIf { count > 0 }))
            }
            super.setShuffleModeEnabled(shuffleModeEnabled)
        }
    }

    private inner class Callback : MediaLibrarySession.Callback {
        private fun isTrusted(session: MediaSession, controller: MediaSession.ControllerInfo) =
            controller.isTrusted || session.isMediaNotificationController(controller) || controller.packageName == packageName

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            if (!isTrusted(session, controller)) {
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(SessionCommands.EMPTY)
                    .setAvailablePlayerCommands(untrustedPlayerCommands)
                    .build()
            }
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(PlaybackCommands.sleepSet)
                .add(PlaybackCommands.sleepCancel)
                .add(PlaybackCommands.moveInPlayOrder)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands).build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = if (isTrusted(session, browser)) {
            Futures.immediateFuture(LibraryResult.ofItem(browseTree.root, params))
        } else {
            Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = future {
            if (!isTrusted(session, browser)) return@future LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)
            browseTree.item(mediaId)?.let { LibraryResult.ofItem(it, null) } ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            if (!isTrusted(session, browser)) return@future LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)
            val all = browseTree.children(parentId)
            val from = (page.toLong() * pageSize).coerceAtMost(all.size.toLong()).toInt()
            val to = (from.toLong() + pageSize).coerceAtMost(all.size.toLong()).toInt()
            LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = future { resolve(mediaItems).map { it.second } }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaItemsWithStartPosition> = future {
            val resolved = resolve(mediaItems)
            // If the chosen song no longer exists, start from the next one that does.
            val newStart = if (startIndex == C.INDEX_UNSET) {
                C.INDEX_UNSET
            } else {
                resolved.indexOfFirst { it.first >= startIndex }.takeIf { it >= 0 } ?: 0
            }
            val keepPosition = newStart != C.INDEX_UNSET && resolved.getOrNull(newStart)?.first == startIndex
            MediaItemsWithStartPosition(resolved.map { it.second }, newStart, if (keepPosition) startPositionMs else 0)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaItemsWithStartPosition> = future {
            val restored = container.queueStore.restore() ?: throw UnsupportedOperationException("Nothing to resume")
            MediaItemsWithStartPosition(restored.items, restored.index, restored.positionMs)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PlaybackCommands.SLEEP_SET -> {
                    val fade = args.getBoolean(PlaybackCommands.ARG_FADE)
                    if (args.getBoolean(PlaybackCommands.ARG_END_OF_TRACK)) {
                        sleepTimer.setEndOfTrack(fade)
                    } else {
                        sleepTimer.setMinutes(args.getInt(PlaybackCommands.ARG_MINUTES), fade)
                    }
                }
                PlaybackCommands.SLEEP_CANCEL -> sleepTimer.cancel()
                PlaybackCommands.MOVE_IN_PLAY_ORDER -> {
                    val order = exo.shuffleOrder as? QueueShuffleOrder
                    if (order != null && order.length == exo.mediaItemCount) {
                        val moved = order.sequence.movedInPlayOrder(args.getInt(PlaybackCommands.ARG_FROM), args.getInt(PlaybackCommands.ARG_TO))
                        exo.setShuffleOrder(QueueShuffleOrder(moved))
                    }
                }
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /** Looks up requested IDs in the library. Returns (original index, playable item) for songs that exist. */
    private suspend fun resolve(requested: List<MediaItem>): List<Pair<Int, MediaItem>> {
        val ids = requested.map { MediaItems.trackId(it.mediaId) }
        val tracks = container.library.tracksByIds(ids.filterNotNull()).associateBy { it.id }
        return requested.mapIndexedNotNull { index, item ->
            val track = ids[index]?.let(tracks::get) ?: return@mapIndexedNotNull null
            index to MediaItems.playable(track, MediaItems.isManual(item))
        }
    }

    /** Runs [block] on the service scope and exposes it as the future Media3 expects. */
    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        scope.launch {
            try {
                result.set(block())
            } catch (cancelled: CancellationException) {
                result.cancel(false)
                throw cancelled
            } catch (error: Exception) {
                result.setException(error)
            }
        }
        return result
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 1_000L
        const val MAX_ERRORS_IN_A_ROW = 5
        val untrustedPlayerCommands: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA,
            )
            .build()
    }
}

/** Custom commands Pocket Box's own UI sends to the session. */
object PlaybackCommands {
    const val SLEEP_SET = "pocketbox.sleep.set"
    const val SLEEP_CANCEL = "pocketbox.sleep.cancel"
    const val MOVE_IN_PLAY_ORDER = "pocketbox.queue.move_in_play_order"
    const val ARG_MINUTES = "minutes"
    const val ARG_END_OF_TRACK = "end_of_track"
    const val ARG_FADE = "fade"
    const val ARG_FROM = "from"
    const val ARG_TO = "to"
    val sleepSet = SessionCommand(SLEEP_SET, Bundle.EMPTY)
    val sleepCancel = SessionCommand(SLEEP_CANCEL, Bundle.EMPTY)
    val moveInPlayOrder = SessionCommand(MOVE_IN_PLAY_ORDER, Bundle.EMPTY)
}
