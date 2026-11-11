// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain.model

/** Codec or container of an audio file, as read from the file itself. */
enum class AudioCodec(val label: String, val isLossless: Boolean?) {
    MP3("MP3", false),
    MP2("MP2", false),
    AAC("AAC", false),
    HE_AAC("HE-AAC", false),
    OPUS("Opus", false),
    VORBIS("Vorbis", false),
    FLAC("FLAC", true),
    ALAC("ALAC", true),
    WAV_PCM("WAV", true),
    WAV_FLOAT("WAV (float)", true),
    WAV_ADPCM("WAV (ADPCM)", false),
    AIFF("AIFF", true),
    AIFF_FLOAT("AIFF (float)", true),
    UNKNOWN("Unknown", null),
    ;

    companion object {
        /** Restores a codec from its stored name; unknown names never crash. */
        fun fromName(name: String?): AudioCodec = entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}

/** How the bitrate was determined. Only labelled constant or variable when the file says so. */
enum class BitrateMode { CONSTANT, VARIABLE, AVERAGE, UNKNOWN }

/** The quality badge shown next to a track. */
enum class QualityClass(val label: String) {
    LOSSY("Lossy"),
    LOSSLESS("Lossless"),
    HI_RES_LOSSLESS("Hi-Res Lossless"),
}

/**
 * Source quality of one file. Every field is read from the file; anything the file does not state
 * is null rather than guessed.
 */
data class AudioQuality(
    val codec: AudioCodec,
    val sampleRateHz: Int?,
    val bitDepth: Int?,
    val bitrateBps: Int?,
    val bitrateMode: BitrateMode,
    val channels: Int?,
    val fileSizeBytes: Long,
    val durationMs: Long,
) {
    val qualityClass: QualityClass? get() = QualityClassifier.classify(codec, sampleRateHz, bitDepth)
}

/** Lossy / Lossless / Hi-Res classification (Hi-Res = lossless at 24-bit or above, or above 48 kHz). */
object QualityClassifier {
    private const val HI_RES_MIN_BIT_DEPTH = 24
    private const val HI_RES_ABOVE_SAMPLE_RATE = 48_000

    fun classify(codec: AudioCodec, sampleRateHz: Int?, bitDepth: Int?): QualityClass? = when (codec.isLossless) {
        null -> null
        false -> QualityClass.LOSSY
        true -> if ((bitDepth ?: 0) >= HI_RES_MIN_BIT_DEPTH || (sampleRateHz ?: 0) > HI_RES_ABOVE_SAMPLE_RATE) {
            QualityClass.HI_RES_LOSSLESS
        } else {
            QualityClass.LOSSLESS
        }
    }
}
