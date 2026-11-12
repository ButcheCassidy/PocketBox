// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.playback

import io.github.butchecassidy.pocketbox.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** A tap waiting for the user to confirm "Clear queue?". */
data class PendingPlay(val trackIds: List<Long>, val startIndex: Int, val shuffle: Boolean, val startPositionMs: Long = 0)

/** Things the UI should tell the user about (as a snackbar). */
sealed interface QueueEvent {
    data object Replaced : QueueEvent
}

/**
 * The "tapping a song" rules:
 * - Tapping a song in any list replaces the whole queue with that list, starting at the tapped song.
 * - If the queue holds songs the user added by hand, ask first ("Clear queue?"), unless they chose
 *   "Don't ask again".
 * - After replacing, offer Undo. Every other queue change can be undone from the queue sheet too.
 */
class QueueController(
    private val player: PlayerConnection,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _pending = MutableStateFlow<PendingPlay?>(null)
    private val _events = MutableSharedFlow<QueueEvent>(extraBufferCapacity = 1)
    private val _canUndo = MutableStateFlow(false)
    private var undoSnapshot: QueueSnapshot? = null

    val pending: StateFlow<PendingPlay?> = _pending.asStateFlow()
    val events: SharedFlow<QueueEvent> = _events.asSharedFlow()
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    /** A song tapped in a list of [trackIds] at [index]. */
    fun play(trackIds: List<Long>, index: Int) = request(PendingPlay(trackIds, index, shuffle = false))

    /** An audiobook: its chapters in order, starting [positionMs] into chapter [index]. Never shuffled or repeated. */
    fun playBook(chapterIds: List<Long>, index: Int, positionMs: Long) =
        request(PendingPlay(chapterIds, index, shuffle = false, startPositionMs = positionMs))

    /** A "Shuffle" button: replaces the queue the same way and turns shuffle on. */
    fun shufflePlay(trackIds: List<Long>) {
        if (trackIds.isEmpty()) return
        request(PendingPlay(trackIds, Random.nextInt(trackIds.size), shuffle = true))
    }

    private fun request(play: PendingPlay) {
        if (play.trackIds.isEmpty()) return
        scope.launch {
            val mustAsk = player.queue.value.hasManualEntries && settings.current().confirmQueueReplace
            if (mustAsk) _pending.value = play else replace(play)
        }
    }

    fun confirm(dontAskAgain: Boolean) {
        val play = _pending.value ?: return
        _pending.value = null
        scope.launch {
            if (dontAskAgain) settings.update { it.copy(confirmQueueReplace = false) }
            replace(play)
        }
    }

    fun cancel() {
        _pending.value = null
    }

    private fun replace(play: PendingPlay) {
        val hadQueue = player.queue.value.entries.isNotEmpty()
        remember()
        player.setQueue(play.trackIds, play.startIndex, play.shuffle, play.startPositionMs)
        if (hadQueue) _events.tryEmit(QueueEvent.Replaced)
    }

    fun playNext(trackIds: List<Long>) = change { player.playNext(trackIds) }
    fun addToQueue(trackIds: List<Long>) = change { player.addToQueue(trackIds) }
    fun remove(index: Int) = change { player.removeAt(index) }
    fun move(fromPosition: Int, toPosition: Int) = change { player.move(fromPosition, toPosition) }
    fun clear() = change { player.clear() }

    fun undo() {
        val snapshot = undoSnapshot ?: return
        undoSnapshot = null
        _canUndo.value = false
        player.restore(snapshot)
    }

    private inline fun change(block: () -> Unit) {
        remember()
        block()
    }

    private fun remember() {
        undoSnapshot = player.snapshot()
        _canUndo.value = undoSnapshot != null
    }
}
