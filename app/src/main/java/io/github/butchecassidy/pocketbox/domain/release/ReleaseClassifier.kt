// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain.release

import io.github.butchecassidy.pocketbox.domain.model.ReleaseType
import java.util.Locale

/**
 * Decides whether a release is an album, a single or an EP.
 *
 * 1. A `RELEASETYPE` / `MusicBrainz Album Type` tag wins when it names a type (its first value is the
 *    primary type, e.g. "album; compilation").
 * 2. Otherwise: 1–3 tracks is a single; 4–6 tracks under 30 minutes is an EP; anything else is an album.
 *
 * A per-release user override is applied on top of this by the repository.
 */
object ReleaseClassifier {
    private const val SINGLE_MAX_TRACKS = 3
    private const val EP_MAX_TRACKS = 6
    private const val EP_MAX_DURATION_MS = 30L * 60 * 1000
    private val separators = Regex("[;/,|\\u0000]")
    private val albumLikeTypes = setOf(
        "album", "compilation", "soundtrack", "live", "remix", "mixtape", "dj-mix",
        "spokenword", "interview", "audiobook", "audio drama", "demo", "broadcast", "other",
    )

    fun classify(releaseTypeTag: String?, trackCount: Int, totalDurationMs: Long): ReleaseType =
        fromTag(releaseTypeTag) ?: fromShape(trackCount, totalDurationMs)

    /** Reads the primary type from a tag value, or null when the tag says nothing useful. */
    fun fromTag(tag: String?): ReleaseType? {
        val primary = tag?.split(separators)
            ?.map { it.trim().lowercase(Locale.ROOT) }
            ?.firstOrNull { it.isNotEmpty() }
            ?: return null
        return when {
            primary == "single" -> ReleaseType.SINGLE
            primary == "ep" -> ReleaseType.EP
            primary in albumLikeTypes -> ReleaseType.ALBUM
            else -> null
        }
    }

    fun fromShape(trackCount: Int, totalDurationMs: Long): ReleaseType = when {
        trackCount in 1..SINGLE_MAX_TRACKS -> ReleaseType.SINGLE
        trackCount <= EP_MAX_TRACKS && trackCount > 0 && totalDurationMs < EP_MAX_DURATION_MS -> ReleaseType.EP
        else -> ReleaseType.ALBUM
    }
}
