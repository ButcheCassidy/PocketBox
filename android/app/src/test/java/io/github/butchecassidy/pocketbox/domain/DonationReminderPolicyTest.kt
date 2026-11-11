// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain

import io.github.butchecassidy.pocketbox.SupportConfig
import io.github.butchecassidy.pocketbox.domain.model.DonationState
import io.github.butchecassidy.pocketbox.domain.model.DonationStatus
import io.github.butchecassidy.pocketbox.domain.support.DonationReminderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DonationReminderPolicyTest {
    private fun state(playDays: Collection<Long>, status: DonationStatus = DonationStatus.NEVER_SHOWN, lastShown: Long? = null) =
        DonationState(firstLaunchEpochDay = 0, playDays = playDays.toSet(), status = status, lastShownEpochDay = lastShown, remindersEnabled = true)

    private fun shows(state: DonationState, today: Long) = DonationReminderPolicy.shouldShow(state, today, linkConfigured = true)

    @Test
    fun `day 29 is too early`() {
        assertFalse(shows(state((0L..29L).toList()), today = 29))
    }

    @Test
    fun `day 30 with only 6 play days is not enough`() {
        assertFalse(shows(state(listOf(25L, 26, 27, 28, 29, 30)), today = 30))
    }

    @Test
    fun `day 34 with 7 play days in the last 30 shows`() {
        assertTrue(shows(state(listOf(5L, 10, 15, 20, 25, 30, 34)), today = 34))
    }

    @Test
    fun `play days older than 30 days don't count`() {
        assertFalse(shows(state(listOf(1L, 2, 3, 4, 30, 31, 34)), today = 34))
    }

    @Test
    fun `maybe later shows once more after 60 days, then never`() {
        val days = (40L..100L).toList()
        val afterFirst = DonationReminderPolicy.afterMaybeLater(DonationStatus.NEVER_SHOWN)
        assertEquals(DonationStatus.MAYBE_LATER, afterFirst)
        assertFalse(shows(state(days, afterFirst, lastShown = 34), today = 93))
        assertTrue(shows(state(days, afterFirst, lastShown = 34), today = 94))
        val afterSecond = DonationReminderPolicy.afterMaybeLater(afterFirst)
        assertEquals(DonationStatus.FINISHED, afterSecond)
        assertFalse(shows(state(days, afterSecond, lastShown = 94), today = 400))
    }

    @Test
    fun `no thanks or donate means never`() {
        assertFalse(shows(state((10L..40L).toList(), DonationStatus.FINISHED, lastShown = 34), today = 40))
    }

    @Test
    fun `placeholder link never shows`() {
        assertFalse(SupportConfig.isConfigured())
        assertFalse(DonationReminderPolicy.shouldShow(state((10L..40L).toList()), 40, linkConfigured = false))
    }
}
