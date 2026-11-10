// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder
import io.github.butchecassidy.pocketbox.domain.queue.ShuffleSequence

/** Media3's [ShuffleOrder] backed by Pocket Box's [ShuffleSequence] rules. */
class QueueShuffleOrder(val sequence: ShuffleSequence) : ShuffleOrder {
    override fun getLength(): Int = sequence.size
    override fun getNextIndex(index: Int): Int = sequence.next(index) ?: C.INDEX_UNSET
    override fun getPreviousIndex(index: Int): Int = sequence.previous(index) ?: C.INDEX_UNSET
    override fun getLastIndex(): Int = sequence.last() ?: C.INDEX_UNSET
    override fun getFirstIndex(): Int = sequence.first() ?: C.INDEX_UNSET

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder =
        QueueShuffleOrder(sequence.inserted(insertionIndex, insertionCount))

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder =
        QueueShuffleOrder(sequence.removed(indexFrom, indexToExclusive))

    override fun cloneAndMove(indexFrom: Int, indexToExclusive: Int, newIndexFrom: Int): ShuffleOrder =
        QueueShuffleOrder(sequence.listMoved(indexFrom, indexToExclusive, newIndexFrom))

    /** A whole new queue: shuffle it with the starting song first. */
    override fun cloneAndSet(insertionCount: Int, startIndex: Int): ShuffleOrder =
        startingAt(insertionCount, startIndex.takeIf { it in 0 until insertionCount })

    override fun cloneAndClear(): ShuffleOrder = QueueShuffleOrder(ShuffleSequence.create(0, null))

    companion object {
        fun startingAt(size: Int, firstIndex: Int?) = QueueShuffleOrder(ShuffleSequence.create(size, firstIndex))
    }
}
