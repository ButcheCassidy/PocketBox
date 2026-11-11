// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data

import io.github.butchecassidy.pocketbox.data.scan.integrity.IntegrityInspector
import io.github.butchecassidy.pocketbox.data.scan.tags.ArrayByteSource
import io.github.butchecassidy.pocketbox.data.scan.tags.AudioFileReader
import io.github.butchecassidy.pocketbox.data.scan.tags.ReadOptions
import io.github.butchecassidy.pocketbox.domain.lyrics.LrcParser
import io.github.butchecassidy.pocketbox.domain.playlist.M3u
import org.junit.Test
import kotlin.random.Random

/**
 * Every file is untrusted: damaged, cut-off and deliberately malformed files must never hang the
 * scanner or escape the integrity check. Each fixture is mutated thousands of ways with a fixed seed,
 * so a failure is reproducible.
 */
class MalformedFileTest {
    private val random = Random(SEED)

    /** Bit flips, overwritten size fields, cut-offs and junk runs: the damage real files show. */
    private fun mutate(original: ByteArray): ByteArray = when (random.nextInt(4)) {
        0 -> original.copyOf().also { bytes -> repeat(1 + random.nextInt(8)) { bytes.flip() } }
        1 -> original.copyOf().also { bytes ->
            val at = random.nextInt(bytes.size)
            for (i in at until minOf(at + 4, bytes.size)) bytes[i] = if (random.nextBoolean()) 0xFF.toByte() else 0
        }
        2 -> original.copyOf(random.nextInt(original.size))
        else -> original.copyOf().also { bytes ->
            val at = random.nextInt(bytes.size)
            for (i in at until minOf(at + 1 + random.nextInt(64), bytes.size)) bytes[i] = random.nextInt().toByte()
        }
    }

    private fun ByteArray.flip() {
        val at = random.nextInt(size)
        this[at] = (this[at].toInt() xor (1 shl random.nextInt(8))).toByte()
    }

    @Test(timeout = TIMEOUT_MS)
    fun `damaged audio files never hang the readers and never crash the integrity check`() {
        for (name in Fixtures.audioFiles) {
            val original = Fixtures.bytes(name)
            repeat(MUTATIONS) {
                val damaged = mutate(original)
                // The scanner catches reader exceptions; what matters is that every read finishes.
                runCatching { AudioFileReader.read(ArrayByteSource(damaged), ReadOptions.SCAN) }
                IntegrityInspector.inspect(ArrayByteSource(damaged))
            }
        }
    }

    @Test(timeout = TIMEOUT_MS)
    fun `random text never breaks the lyrics and playlist parsers`() {
        val alphabet = "[]:.0123456789-abc#EXTM3U\n\r\t/\\%~ "
        repeat(MUTATIONS) {
            val text = String(CharArray(random.nextInt(400)) { alphabet[random.nextInt(alphabet.length)] })
            LrcParser.parse(text)
            M3u.parse(text)
        }
    }

    private companion object {
        const val SEED = 20261111
        const val MUTATIONS = 2_000
        const val TIMEOUT_MS = 120_000L
    }
}
