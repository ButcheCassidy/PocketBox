// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain

import io.github.butchecassidy.pocketbox.domain.model.AudioCodec
import io.github.butchecassidy.pocketbox.domain.model.QualityClass
import io.github.butchecassidy.pocketbox.domain.model.QualityClassifier
import io.github.butchecassidy.pocketbox.domain.model.ReleaseType
import io.github.butchecassidy.pocketbox.domain.release.ReleaseClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseAndQualityTest {
    private val minute = 60_000L

    @Test
    fun `tag decides the release type`() {
        assertEquals(ReleaseType.SINGLE, ReleaseClassifier.classify("single", 12, 60 * minute))
        assertEquals(ReleaseType.EP, ReleaseClassifier.classify("EP", 1, minute))
        assertEquals(ReleaseType.ALBUM, ReleaseClassifier.classify("album; compilation", 2, minute))
    }

    @Test
    fun `unknown tag falls back to track count and length`() {
        assertEquals(ReleaseType.SINGLE, ReleaseClassifier.classify("weird", 3, 10 * minute))
        assertEquals(ReleaseType.SINGLE, ReleaseClassifier.classify(null, 1, 4 * minute))
        assertEquals(ReleaseType.EP, ReleaseClassifier.classify(null, 4, 20 * minute))
        assertEquals(ReleaseType.EP, ReleaseClassifier.classify(null, 6, 29 * minute))
        assertEquals(ReleaseType.ALBUM, ReleaseClassifier.classify(null, 6, 30 * minute))
        assertEquals(ReleaseType.ALBUM, ReleaseClassifier.classify(null, 7, 20 * minute))
    }

    @Test
    fun `quality badges`() {
        assertEquals(QualityClass.LOSSY, QualityClassifier.classify(AudioCodec.MP3, 96_000, null))
        assertEquals(QualityClass.LOSSLESS, QualityClassifier.classify(AudioCodec.FLAC, 44_100, 16))
        assertEquals(QualityClass.LOSSLESS, QualityClassifier.classify(AudioCodec.ALAC, 48_000, 16))
        assertEquals(QualityClass.HI_RES_LOSSLESS, QualityClassifier.classify(AudioCodec.FLAC, 44_100, 24))
        assertEquals(QualityClass.HI_RES_LOSSLESS, QualityClassifier.classify(AudioCodec.WAV_PCM, 88_200, 16))
        assertEquals(QualityClass.LOSSLESS, QualityClassifier.classify(AudioCodec.AIFF, 48_000, null))
        assertNull(QualityClassifier.classify(AudioCodec.UNKNOWN, 192_000, 24))
    }
}
