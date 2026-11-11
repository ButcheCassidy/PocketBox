// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain.audio

/** A named set of 10 equalizer band gains in dB, for 31 Hz … 16 kHz. */
data class EqPreset(val name: String, val bandsDb: List<Float>, val isBuiltIn: Boolean, val id: Long = 0)

/** The equalizer's bands and built-in presets. */
object EqPresets {
    val bandCentersHz = listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)
    const val MIN_DB = -12f
    const val MAX_DB = 12f

    /** Extra low-frequency gain at full bass boost, per band (the top seven bands are untouched). */
    private val bassBoostShapeDb = listOf(8f, 7f, 4.5f, 1.5f, 0f, 0f, 0f, 0f, 0f, 0f)

    val builtIn: List<EqPreset> = listOf(
        preset("Flat", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        preset("Bass", 6, 5, 4, 2, 0, 0, 0, 0, 0, 0),
        preset("Treble", 0, 0, 0, 0, 0, 1, 2, 4, 5, 6),
        preset("Vocal", -2, -2, -1, 1, 3, 4, 3, 1, 0, -1),
        preset("Rock", 4, 3, 2, 0, -1, -1, 1, 2, 3, 4),
        preset("Pop", -1, 0, 2, 3, 4, 3, 1, 0, -1, -1),
        preset("Jazz", 3, 2, 1, 2, -1, -1, 0, 1, 2, 3),
        preset("Classical", 4, 3, 2, 1, 0, 0, 0, 1, 2, 3),
        preset("Electronic", 5, 4, 1, 0, -2, 1, 0, 1, 4, 5),
        preset("Acoustic", 3, 3, 2, 1, 1, 1, 2, 2, 2, 1),
        preset("Late night", -3, -2, -1, 0, 1, 2, 2, 1, -1, -2),
    )

    /** Final per-band gains: the user's EQ (if on) plus the bass boost curve, clamped to a safe range. */
    fun effectiveGains(eqEnabled: Boolean, bandsDb: List<Float>, bassBoost: Float): List<Float> =
        bandCentersHz.indices.map { i ->
            val eq = if (eqEnabled) bandsDb.getOrElse(i) { 0f } else 0f
            (eq + bassBoostShapeDb[i] * bassBoost.coerceIn(0f, 1f)).coerceIn(MIN_DB, MAX_DB + bassBoostShapeDb[i])
        }

    private fun preset(name: String, vararg db: Int) = EqPreset(name, db.map { it.toFloat() }, isBuiltIn = true)
}
