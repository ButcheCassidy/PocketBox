// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui

import java.util.Locale

/** Plain-English number formatting shared by every screen. */
object Format {
    /** 185_000 → "3:05"; 3_723_000 → "1:02:03". */
    fun duration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    /** Album lengths: "48 min", "1 h 12 min". */
    fun totalDuration(ms: Long): String {
        val minutes = (ms / 60_000).coerceAtLeast(0)
        return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    }

    /** 44_100 → "44.1 kHz"; 96_000 → "96 kHz". */
    fun sampleRate(hz: Int): String {
        val khz = hz / 1000.0
        return if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else String.format(Locale.ROOT, "%.1f kHz", khz)
    }

    fun bitrate(bps: Int): String = "${(bps + 500) / 1000} kbps"

    fun fileSize(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format(Locale.ROOT, "%.2f GB", bytes / 1e9)
        bytes >= 1_000_000 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1e6)
        bytes >= 1_000 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1e3)
        else -> "$bytes B"
    }

    fun decibels(db: Float): String = String.format(Locale.ROOT, "%+.2f dB", db)
}
