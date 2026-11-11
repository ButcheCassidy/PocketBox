// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.theme.color

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * HCT (hue, chroma, tone): the colour space Material Design uses to build tonal palettes. Hue and
 * chroma come from the CAM16 colour appearance model; tone is CIE L* (perceived lightness), so two
 * colours with a large tone difference always have good contrast.
 *
 * This is a compact, dependency-free implementation of the published CAM16/HCT maths, under the
 * default viewing conditions Material uses (D65, 200/π lux, background L* 50, average surround).
 */
object Hct {
    /** Hue (0–360), chroma and tone (0–100) of an ARGB colour. */
    fun fromArgb(argb: Int): Triple<Double, Double, Double> {
        val cam = Cam16.fromArgb(argb)
        return Triple(cam.hue, cam.chroma, ColorUtils.lstarFromArgb(argb))
    }

    /**
     * The sRGB colour closest to the requested hue and chroma at exactly [tone]. When the chroma
     * isn't reachable at that tone (sRGB can't show it), chroma is reduced until it is.
     */
    fun toArgb(hue: Double, chroma: Double, tone: Double): Int {
        if (chroma < 1.0 || tone.roundToInt() <= 0 || tone.roundToInt() >= 100) return ColorUtils.argbFromLstar(tone)
        val h = sanitizeDegrees(hue)
        var high = chroma
        var mid = chroma
        var low = 0.0
        var firstLoop = true
        var answer: Cam16? = null
        while (abs(low - high) >= CHROMA_SEARCH_ENDPOINT) {
            val possible = findCamByJ(h, mid, tone)
            if (firstLoop) {
                if (possible != null) return possible.toArgb()
                firstLoop = false
            }
            if (possible == null) {
                high = mid
            } else {
                answer = possible
                low = mid
            }
            mid = low + (high - low) / 2.0
        }
        return answer?.toArgb() ?: ColorUtils.argbFromLstar(tone)
    }

    /** Binary-searches CAM16 lightness J for a colour that lands on [tone] without visible hue drift. */
    private fun findCamByJ(hue: Double, chroma: Double, tone: Double): Cam16? {
        var low = 0.0
        var high = 100.0
        var bestDeltaL = 1000.0
        var bestDeltaE = 1000.0
        var best: Cam16? = null
        while (abs(low - high) > LIGHTNESS_SEARCH_ENDPOINT) {
            val mid = low + (high - low) / 2.0
            val clipped = Cam16.fromJch(mid, chroma, hue).toArgb()
            val clippedTone = ColorUtils.lstarFromArgb(clipped)
            val deltaL = abs(tone - clippedTone)
            if (deltaL < DL_MAX) {
                val clippedCam = Cam16.fromArgb(clipped)
                val deltaE = clippedCam.distance(Cam16.fromJch(clippedCam.j, clippedCam.chroma, hue))
                if (deltaE <= DE_MAX && deltaE <= bestDeltaE) {
                    bestDeltaL = deltaL
                    bestDeltaE = deltaE
                    best = clippedCam
                }
            }
            if (bestDeltaL == 0.0 && bestDeltaE == 0.0) break
            if (clippedTone < tone) low = mid else high = mid
        }
        return best
    }

    fun sanitizeDegrees(degrees: Double): Double {
        val d = degrees % 360.0
        return if (d < 0) d + 360.0 else d
    }

    private const val CHROMA_SEARCH_ENDPOINT = 0.4
    private const val LIGHTNESS_SEARCH_ENDPOINT = 0.01
    private const val DL_MAX = 0.2
    private const val DE_MAX = 1.0
}

/** CAM16 colour appearance: lightness J, chroma, hue, and the extras needed for distance. */
internal class Cam16(val hue: Double, val chroma: Double, val j: Double, private val m: Double) {
    private val jStar = (1.0 + 100.0 * 0.007) * j / (1.0 + 0.007 * j)
    private val mStar = 1.0 / 0.0228 * ln(1.0 + 0.0228 * m)
    private val aStar = mStar * cos(Math.toRadians(hue))
    private val bStar = mStar * sin(Math.toRadians(hue))

    /** Perceptual distance in CAM16-UCS. */
    fun distance(other: Cam16): Double {
        val dJ = jStar - other.jStar
        val dA = aStar - other.aStar
        val dB = bStar - other.bStar
        return 1.41 * sqrt(dJ * dJ + dA * dA + dB * dB).pow(0.63)
    }

    fun toArgb(): Int {
        val vc = ViewingConditions
        val alpha = if (chroma == 0.0 || j == 0.0) 0.0 else chroma / sqrt(j / 100.0)
        val t = (alpha / (1.64 - 0.29.pow(vc.n)).pow(0.73)).pow(1.0 / 0.9)
        val hRad = Math.toRadians(hue)
        val eHue = 0.25 * (cos(hRad + 2.0) + 3.8)
        val ac = vc.aw * (j / 100.0).pow(1.0 / vc.c / vc.z)
        val p1 = eHue * (50000.0 / 13.0) * vc.nc * vc.ncb
        val p2 = ac / vc.nbb
        val hSin = sin(hRad)
        val hCos = cos(hRad)
        val gamma = 23.0 * (p2 + 0.305) * t / (23.0 * p1 + 11.0 * t * hCos + 108.0 * t * hSin)
        val a = gamma * hCos
        val b = gamma * hSin
        val rA = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0
        val gA = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0
        val bA = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0
        fun undoAdaptation(component: Double): Double {
            val base = maxOf(0.0, 27.13 * abs(component) / (400.0 - abs(component)))
            return sign(component) * (100.0 / vc.fl) * base.pow(1.0 / 0.42)
        }
        val rF = undoAdaptation(rA) / vc.rgbD[0]
        val gF = undoAdaptation(gA) / vc.rgbD[1]
        val bF = undoAdaptation(bA) / vc.rgbD[2]
        val x = 1.86206786 * rF - 1.01125463 * gF + 0.14918677 * bF
        val y = 0.38752654 * rF + 0.62144744 * gF - 0.00897398 * bF
        val z = -0.01584150 * rF - 0.03412294 * gF + 1.04996444 * bF
        return ColorUtils.argbFromXyz(x, y, z)
    }

    companion object {
        fun fromArgb(argb: Int): Cam16 {
            val vc = ViewingConditions
            val red = ColorUtils.linearized((argb shr 16) and 0xFF)
            val green = ColorUtils.linearized((argb shr 8) and 0xFF)
            val blue = ColorUtils.linearized(argb and 0xFF)
            val x = 0.41233895 * red + 0.35762064 * green + 0.18051042 * blue
            val y = 0.2126 * red + 0.7152 * green + 0.0722 * blue
            val z = 0.01932141 * red + 0.11916382 * green + 0.95034478 * blue
            val rC = 0.401288 * x + 0.650173 * y - 0.051461 * z
            val gC = -0.250268 * x + 1.204414 * y + 0.045854 * z
            val bC = -0.002079 * x + 0.048952 * y + 0.953127 * z
            fun adapt(component: Double, d: Double): Double {
                val af = (vc.fl * abs(d * component) / 100.0).pow(0.42)
                return sign(d * component) * 400.0 * af / (af + 27.13)
            }
            val rA = adapt(rC, vc.rgbD[0])
            val gA = adapt(gC, vc.rgbD[1])
            val bA = adapt(bC, vc.rgbD[2])
            val a = (11.0 * rA - 12.0 * gA + bA) / 11.0
            val b = (rA + gA - 2.0 * bA) / 9.0
            val u = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0
            val p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0
            val hue = Hct.sanitizeDegrees(Math.toDegrees(atan2(b, a)))
            val ac = p2 * vc.nbb
            val j = 100.0 * (ac / vc.aw).pow(vc.c * vc.z)
            val huePrime = if (hue < 20.14) hue + 360 else hue
            val eHue = 0.25 * (cos(Math.toRadians(huePrime) + 2.0) + 3.8)
            val p1 = 50000.0 / 13.0 * eHue * vc.nc * vc.ncb
            val t = p1 * hypot(a, b) / (u + 0.305)
            val alpha = t.pow(0.9) * (1.64 - 0.29.pow(vc.n)).pow(0.73)
            val chroma = alpha * sqrt(j / 100.0)
            return Cam16(hue, chroma, j, chroma * vc.fl.pow(0.25))
        }

        fun fromJch(j: Double, chroma: Double, hue: Double): Cam16 =
            Cam16(hue, chroma, j, chroma * ViewingConditions.fl.pow(0.25))
    }
}

/** Material's default viewing conditions, computed once. */
internal object ViewingConditions {
    private val whitePoint = doubleArrayOf(95.047, 100.0, 108.883)
    private val adaptingLuminance = 200.0 / Math.PI * ColorUtils.yFromLstar(50.0) / 100.0
    private const val SURROUND = 2.0

    val n: Double
    val aw: Double
    val nbb: Double
    val ncb: Double
    val c: Double
    val nc: Double
    val rgbD: DoubleArray
    val fl: Double
    val z: Double

    init {
        val (x, y, zw) = Triple(whitePoint[0], whitePoint[1], whitePoint[2])
        val rW = x * 0.401288 + y * 0.650173 + zw * -0.051461
        val gW = x * -0.250268 + y * 1.204414 + zw * 0.045854
        val bW = x * -0.002079 + y * 0.048952 + zw * 0.953127
        val f = 0.8 + SURROUND / 10.0
        c = if (f >= 0.9) lerp(0.59, 0.69, (f - 0.9) * 10.0) else lerp(0.525, 0.59, (f - 0.8) * 10.0)
        val d = (f * (1.0 - 1.0 / 3.6 * exp((-adaptingLuminance - 42.0) / 92.0))).coerceIn(0.0, 1.0)
        nc = f
        rgbD = doubleArrayOf(d * (100.0 / rW) + 1.0 - d, d * (100.0 / gW) + 1.0 - d, d * (100.0 / bW) + 1.0 - d)
        val k = 1.0 / (5.0 * adaptingLuminance + 1.0)
        val k4 = k * k * k * k
        val k4F = 1.0 - k4
        fl = k4 * adaptingLuminance + 0.1 * k4F * k4F * cbrt(5.0 * adaptingLuminance)
        n = ColorUtils.yFromLstar(50.0) / whitePoint[1]
        z = 1.48 + sqrt(n)
        nbb = 0.725 / n.pow(0.2)
        ncb = nbb
        fun adapted(component: Double, dComponent: Double): Double {
            val factor = (fl * dComponent * component / 100.0).pow(0.42)
            return 400.0 * factor / (factor + 27.13)
        }
        val rA = adapted(rW, rgbD[0])
        val gA = adapted(gW, rgbD[1])
        val bA = adapted(bW, rgbD[2])
        aw = (2.0 * rA + gA + 0.05 * bA) * nbb
    }

    private fun lerp(start: Double, stop: Double, amount: Double) = (1.0 - amount) * start + amount * stop
}

/** sRGB ↔ linear RGB ↔ XYZ ↔ L*, and WCAG contrast. */
object ColorUtils {
    private const val EPSILON = 216.0 / 24389.0
    private const val KAPPA = 24389.0 / 27.0

    fun linearized(component: Int): Double {
        val normalized = component / 255.0
        return if (normalized <= 0.040449936) normalized / 12.92 * 100.0 else ((normalized + 0.055) / 1.055).pow(2.4) * 100.0
    }

    private fun delinearized(component: Double): Int {
        val normalized = component / 100.0
        val value = if (normalized <= 0.0031308) normalized * 12.92 else 1.055 * normalized.pow(1.0 / 2.4) - 0.055
        return (value * 255.0).roundToInt().coerceIn(0, 255)
    }

    fun argbFromXyz(x: Double, y: Double, z: Double): Int {
        val r = delinearized(3.2413774792388685 * x - 1.5376652402851851 * y - 0.49885366846268053 * z)
        val g = delinearized(-0.9691452513005321 * x + 1.8758853451067872 * y + 0.04156585616912061 * z)
        val b = delinearized(0.05562093689691305 * x - 0.20395524564742123 * y + 1.0571799111220335 * z)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun yFromLstar(lstar: Double): Double {
        val ft = (lstar + 16.0) / 116.0
        val ft3 = ft * ft * ft
        return 100.0 * if (ft3 > EPSILON) ft3 else (116.0 * ft - 16.0) / KAPPA
    }

    fun argbFromLstar(lstar: Double): Int {
        val component = delinearized(yFromLstar(lstar))
        return (0xFF shl 24) or (component shl 16) or (component shl 8) or component
    }

    /** Relative luminance Y (0–100) of an sRGB colour. */
    fun yFromArgb(argb: Int): Double =
        0.2126 * linearized((argb shr 16) and 0xFF) + 0.7152 * linearized((argb shr 8) and 0xFF) +
            0.0722 * linearized(argb and 0xFF)

    fun lstarFromArgb(argb: Int): Double {
        val y = yFromArgb(argb) / 100.0
        return if (y <= EPSILON) y * KAPPA else 116.0 * cbrt(y) - 16.0
    }

    /** WCAG 2 contrast ratio between two opaque colours (1–21). */
    fun contrast(a: Int, b: Int): Double {
        val ya = yFromArgb(a) / 100.0
        val yb = yFromArgb(b) / 100.0
        return (maxOf(ya, yb) + 0.05) / (minOf(ya, yb) + 0.05)
    }
}
