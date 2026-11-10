// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain.queue

import kotlin.random.Random

/**
 * The order shuffled songs play in, as a list of queue indices.
 *
 * Rules:
 * - Turning shuffle on puts the playing song first and every other song once after it, so nothing
 *   repeats until the whole queue has played.
 * - Turning shuffle off simply stops using this order; the queue's own order was never changed.
 * - "Play next" lands right after the song it was inserted behind; "Add to queue" lands at the end.
 */
class ShuffleSequence private constructor(private val order: IntArray) {
    private val positionOf = IntArray(order.size).also { inverse -> order.forEachIndexed { pos, index -> inverse[index] = pos } }

    val size: Int get() = order.size

    /** Queue indices in play order. */
    fun toList(): List<Int> = order.toList()

    fun first(): Int? = order.firstOrNull()
    fun last(): Int? = order.lastOrNull()

    fun next(index: Int): Int? = positionOf.getOrNull(index)?.let { order.getOrNull(it + 1) }
    fun previous(index: Int): Int? = positionOf.getOrNull(index)?.let { order.getOrNull(it - 1) }

    /** Where [index] sits in play order. */
    fun positionOf(index: Int): Int = positionOf[index]

    /** New songs inserted into the queue at [insertionIndex]; existing indices shift up. */
    fun inserted(insertionIndex: Int, count: Int): ShuffleSequence {
        if (count <= 0) return this
        val shifted = order.map { if (it >= insertionIndex) it + count else it }.toMutableList()
        val added = (insertionIndex until insertionIndex + count).toList()
        val at = when {
            order.isEmpty() || insertionIndex >= order.size -> shifted.size
            insertionIndex == 0 -> 0
            else -> positionOf[insertionIndex - 1] + 1
        }
        shifted.addAll(at, added)
        return ShuffleSequence(shifted.toIntArray())
    }

    /** Songs [from] until [toExclusive] removed from the queue; later indices shift down. */
    fun removed(from: Int, toExclusive: Int): ShuffleSequence {
        val count = toExclusive - from
        if (count <= 0) return this
        return ShuffleSequence(
            order.filter { it < from || it >= toExclusive }
                .map { if (it >= toExclusive) it - count else it }
                .toIntArray(),
        )
    }

    /**
     * Queue songs [from] until [toExclusive] moved to start at [newFrom] (reordering with shuffle off).
     * Each song keeps its place in the shuffled play order; only the indices are renumbered.
     */
    fun listMoved(from: Int, toExclusive: Int, newFrom: Int): ShuffleSequence {
        val list = order.indices.toMutableList()
        val moving = list.subList(from, toExclusive).toList()
        list.subList(from, toExclusive).clear()
        list.addAll(newFrom, moving)
        val newIndexOf = IntArray(list.size).also { map -> list.forEachIndexed { newIndex, old -> map[old] = newIndex } }
        return ShuffleSequence(order.map { newIndexOf[it] }.toIntArray())
    }

    /** Moves the song at play position [fromPosition] to [toPosition] (drag to reorder while shuffled). */
    fun movedInPlayOrder(fromPosition: Int, toPosition: Int): ShuffleSequence {
        if (fromPosition !in order.indices || toPosition !in order.indices) return this
        val list = order.toMutableList()
        list.add(toPosition, list.removeAt(fromPosition))
        return ShuffleSequence(list.toIntArray())
    }

    companion object {
        /** A fresh shuffle of [size] songs with [firstIndex] (the playing song) first. */
        fun create(size: Int, firstIndex: Int?, random: Random = Random.Default): ShuffleSequence {
            val rest = (0 until size).filter { it != firstIndex }.shuffled(random)
            val order = if (firstIndex != null && firstIndex in 0 until size) listOf(firstIndex) + rest else rest
            return ShuffleSequence(order.toIntArray())
        }

        /** Rebuilds a saved order; returns null if it doesn't describe exactly [size] songs. */
        fun restore(order: List<Int>, size: Int): ShuffleSequence? =
            if (order.size == size && order.toSet() == (0 until size).toSet()) ShuffleSequence(order.toIntArray()) else null
    }
}
