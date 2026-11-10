// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain

import io.github.butchecassidy.pocketbox.domain.queue.ShuffleSequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class ShuffleSequenceTest {
    private fun playAll(s: ShuffleSequence): List<Int> = generateSequence(s.first()) { s.next(it) }.toList()

    @Test
    fun `playing song first and every song exactly once`() {
        repeat(50) { seed ->
            val s = ShuffleSequence.create(20, firstIndex = 7, random = Random(seed))
            val played = playAll(s)
            assertEquals(7, played.first())
            assertEquals((0 until 20).toSet(), played.toSet())
            assertEquals(20, played.size)
        }
    }

    @Test
    fun `empty and single-song queues`() {
        assertNull(ShuffleSequence.create(0, null).first())
        val one = ShuffleSequence.create(1, 0)
        assertEquals(listOf(0), playAll(one))
        assertNull(one.next(0))
    }

    @Test
    fun `play next lands right after the current song`() {
        val s = ShuffleSequence.restore(listOf(2, 0, 3, 1), 4)!!
        // Current song is 2; "play next" inserts at list index 3 (after index 2).
        val after = s.inserted(insertionIndex = 3, count = 1)
        assertEquals(listOf(2, 3, 0, 4, 1), after.toList())
    }

    @Test
    fun `add to queue lands at the end`() {
        val s = ShuffleSequence.restore(listOf(2, 0, 3, 1), 4)!!
        assertEquals(listOf(2, 0, 3, 1, 4, 5), s.inserted(insertionIndex = 4, count = 2).toList())
    }

    @Test
    fun `removing songs renumbers the rest`() {
        val s = ShuffleSequence.restore(listOf(2, 0, 3, 1), 4)!!
        assertEquals(listOf(1, 2, 0), s.removed(0, 1).toList()) // [2,3,1] renumbered: 2→1, 3→2, 1→0
    }

    @Test
    fun `moving songs in the list keeps their shuffled slots`() {
        val s = ShuffleSequence.restore(listOf(2, 0, 3, 1), 4)!!
        // List [a,b,c,d] → move a to the end → [b,c,d,a]: a=3, b=0, c=1, d=2.
        assertEquals(listOf(1, 3, 2, 0), s.listMoved(0, 1, 3).toList())
    }

    @Test
    fun `dragging within play order`() {
        val s = ShuffleSequence.restore(listOf(2, 0, 3, 1), 4)!!
        assertEquals(listOf(2, 1, 0, 3), s.movedInPlayOrder(3, 1).toList())
    }

    @Test
    fun `restore rejects orders that no longer fit`() {
        assertNull(ShuffleSequence.restore(listOf(0, 1, 1), 3))
        assertNull(ShuffleSequence.restore(listOf(0, 1), 3))
    }
}
