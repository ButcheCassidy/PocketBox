// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain.support

import io.github.butchecassidy.pocketbox.domain.model.DonationState
import io.github.butchecassidy.pocketbox.domain.model.DonationStatus

/**
 * When the friendly, once-only donation reminder may appear:
 * - at least 30 days since first launch, AND music played on at least 7 different days in the last 30;
 * - "Maybe later" asks once more after 60 days (if the play-days rule still holds), then never again;
 * - "No thanks" or "Donate" never ask again; reminders can be switched off in About;
 * - never while the link is still the placeholder.
 */
object DonationReminderPolicy {
    const val DAYS_BEFORE_FIRST = 30
    const val REQUIRED_PLAY_DAYS = 7
    const val PLAY_DAY_WINDOW = 30
    const val DAYS_AFTER_MAYBE_LATER = 60

    fun shouldShow(state: DonationState, todayEpochDay: Long, linkConfigured: Boolean): Boolean {
        if (!linkConfigured || !state.remindersEnabled) return false
        val firstLaunch = state.firstLaunchEpochDay ?: return false
        if (todayEpochDay - firstLaunch < DAYS_BEFORE_FIRST) return false
        val recentPlayDays = state.playDays.count { it in (todayEpochDay - PLAY_DAY_WINDOW + 1)..todayEpochDay }
        if (recentPlayDays < REQUIRED_PLAY_DAYS) return false
        return when (state.status) {
            DonationStatus.NEVER_SHOWN -> true
            DonationStatus.MAYBE_LATER -> {
                val shown = state.lastShownEpochDay ?: return true
                todayEpochDay - shown >= DAYS_AFTER_MAYBE_LATER
            }
            DonationStatus.FINISHED -> false
        }
    }

    /** What "Maybe later" leads to: one more chance the first time, never again after that. */
    fun afterMaybeLater(current: DonationStatus): DonationStatus =
        if (current == DonationStatus.NEVER_SHOWN) DonationStatus.MAYBE_LATER else DonationStatus.FINISHED
}
