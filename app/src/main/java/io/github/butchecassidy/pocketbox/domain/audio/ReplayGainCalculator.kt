// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain.audio

import io.github.butchecassidy.pocketbox.domain.model.ReplayGain
import io.github.butchecassidy.pocketbox.domain.model.ReplayGainMode
import kotlin.math.pow

/** Turns ReplayGain tags into a volume factor (1.0 = unchanged). */
object ReplayGainCalculator {
    /**
     * Track mode prefers track gain, album mode prefers album gain; each falls back to the other.
     * Files without any ReplayGain tag play unchanged. The result never exceeds what the file's
     * peak allows, and never exceeds 1.0, so ReplayGain can't cause clipping.
     */
    fun volumeFactor(tags: ReplayGain, mode: ReplayGainMode, preampDb: Float): Float {
        if (mode == ReplayGainMode.OFF) return 1f
        val (gain, peak) = when (mode) {
            ReplayGainMode.TRACK -> (tags.trackGainDb ?: tags.albumGainDb) to (tags.trackPeak ?: tags.albumPeak)
            else -> (tags.albumGainDb ?: tags.trackGainDb) to (tags.albumPeak ?: tags.trackPeak)
        }
        if (gain == null) return 1f
        val linear = 10.0.pow((gain + preampDb) / 20.0).toFloat()
        val peakLimit = if (peak != null && peak > 0f) 1f / peak else Float.MAX_VALUE
        return minOf(linear, peakLimit, 1f).coerceAtLeast(0f)
    }
}
