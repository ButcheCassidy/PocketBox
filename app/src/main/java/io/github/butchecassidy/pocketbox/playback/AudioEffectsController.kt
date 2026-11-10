// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.playback

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import io.github.butchecassidy.pocketbox.domain.audio.EqPresets
import io.github.butchecassidy.pocketbox.domain.model.UserSettings
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The 10-band equalizer and bass boost, using Android's own audio effects on Pocket Box's audio
 * session only (never system-wide). DynamicsProcessing gives true 10 bands plus a limiter that stops
 * boosts from clipping; if a device can't create it, the platform Equalizer is used with each of its
 * bands set from the nearest Pocket Box band. Effects are off entirely in bit-perfect mode.
 */
class AudioEffectsController(private val audioSessionId: Int) {
    private var dynamics: DynamicsProcessing? = null
    private var fallback: Equalizer? = null
    private var dynamicsUnavailable = false

    fun apply(settings: UserSettings) {
        val active = !settings.bitPerfect && (settings.eqEnabled || settings.bassBoost > 0f)
        if (!active) {
            dynamics?.enabled = false
            fallback?.enabled = false
            return
        }
        val gains = EqPresets.effectiveGains(settings.eqEnabled, settings.eqBandsDb, settings.bassBoost)
        if (!applyDynamics(gains)) applyFallback(gains)
    }

    private fun applyDynamics(gains: List<Float>): Boolean {
        if (dynamicsUnavailable) return false
        val effect = dynamics ?: runCatching { DynamicsProcessing(0, audioSessionId, buildConfig()) }
            .onFailure { dynamicsUnavailable = true }
            .getOrNull()
            ?.also { dynamics = it }
            ?: return false
        return runCatching {
            gains.forEachIndexed { band, gain ->
                effect.setPreEqBandAllChannelsTo(band, DynamicsProcessing.EqBand(true, cutoffs[band], gain))
            }
            effect.enabled = true
        }.isSuccess
    }

    private fun applyFallback(gains: List<Float>) {
        val eq = fallback ?: runCatching { Equalizer(0, audioSessionId) }.getOrNull()?.also { fallback = it } ?: return
        runCatching {
            val (minLevel, maxLevel) = eq.bandLevelRange.let { it[0] to it[1] }
            for (band in 0 until eq.numberOfBands) {
                val centerHz = eq.getCenterFreq(band.toShort()) / 1000f
                val nearest = EqPresets.bandCentersHz.indices.minBy { abs(EqPresets.bandCentersHz[it] - centerHz) }
                val millibels = (gains[nearest] * 100).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt())
                eq.setBandLevel(band.toShort(), millibels.toShort())
            }
            eq.enabled = true
        }
    }

    private fun buildConfig(): DynamicsProcessing.Config {
        val bands = EqPresets.bandCentersHz.size
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNELS,
            true, bands, // pre-EQ: the 10 bands
            false, 0, // multi-band compressor: unused
            false, 0, // post-EQ: unused
            true, // limiter: keeps boosted bands from clipping
        ).build()
        for (band in 0 until bands) {
            config.setPreEqBandAllChannelsTo(band, DynamicsProcessing.EqBand(true, cutoffs[band], 0f))
        }
        config.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(true, true, 0, LIMITER_ATTACK_MS, LIMITER_RELEASE_MS, LIMITER_RATIO, LIMITER_THRESHOLD_DB, 0f),
        )
        return config
    }

    fun release() {
        dynamics?.release()
        dynamics = null
        fallback?.release()
        fallback = null
    }

    private companion object {
        const val CHANNELS = 2
        const val LIMITER_ATTACK_MS = 1f
        const val LIMITER_RELEASE_MS = 60f
        const val LIMITER_RATIO = 10f
        const val LIMITER_THRESHOLD_DB = -1f

        /** Each band's upper edge: halfway (geometrically) to the next band's centre. */
        val cutoffs: List<Float> = EqPresets.bandCentersHz.mapIndexed { i, hz ->
            val next = EqPresets.bandCentersHz.getOrNull(i + 1) ?: return@mapIndexed 20_000f
            sqrt(hz.toFloat() * next)
        }
    }
}
