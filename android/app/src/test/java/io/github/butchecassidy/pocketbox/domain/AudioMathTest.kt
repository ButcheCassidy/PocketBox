// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain

import io.github.butchecassidy.pocketbox.domain.audio.EqPresets
import io.github.butchecassidy.pocketbox.domain.audio.ReplayGainCalculator
import io.github.butchecassidy.pocketbox.domain.model.ReplayGain
import io.github.butchecassidy.pocketbox.domain.model.ReplayGainMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioMathTest {
    private val tags = ReplayGain(trackGainDb = -6f, trackPeak = 0.9f, albumGainDb = -3f, albumPeak = 0.95f)

    @Test
    fun `off and untagged files are unchanged`() {
        assertEquals(1f, ReplayGainCalculator.volumeFactor(tags, ReplayGainMode.OFF, 0f))
        assertEquals(1f, ReplayGainCalculator.volumeFactor(ReplayGain.NONE, ReplayGainMode.TRACK, 0f))
    }

    @Test
    fun `track and album modes pick their own gain`() {
        assertEquals(0.501f, ReplayGainCalculator.volumeFactor(tags, ReplayGainMode.TRACK, 0f), 0.001f)
        assertEquals(0.708f, ReplayGainCalculator.volumeFactor(tags, ReplayGainMode.ALBUM, 0f), 0.001f)
    }

    @Test
    fun `missing gain falls back to the other one`() {
        val trackOnly = ReplayGain(-6f, null, null, null)
        assertEquals(0.501f, ReplayGainCalculator.volumeFactor(trackOnly, ReplayGainMode.ALBUM, 0f), 0.001f)
    }

    @Test
    fun `never boosts above full volume or past the peak`() {
        val quiet = ReplayGain(trackGainDb = 6f, trackPeak = 0.5f, albumGainDb = null, albumPeak = null)
        assertEquals(1f, ReplayGainCalculator.volumeFactor(quiet, ReplayGainMode.TRACK, 3f))
    }

    @Test
    fun `pre-amp is added to the gain`() {
        assertEquals(0.708f, ReplayGainCalculator.volumeFactor(tags, ReplayGainMode.TRACK, 3f), 0.001f)
    }

    @Test
    fun `equalizer curve adds bass boost only to low bands`() {
        val gains = EqPresets.effectiveGains(eqEnabled = false, bandsDb = List(10) { 5f }, bassBoost = 1f)
        assertEquals(8f, gains[0])
        assertEquals(0f, gains[9])
        val withEq = EqPresets.effectiveGains(eqEnabled = true, bandsDb = List(10) { 2f }, bassBoost = 0f)
        assertEquals(List(10) { 2f }, withEq)
    }
}
