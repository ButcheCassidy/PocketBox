// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.lyrics

import androidx.core.net.toUri
import io.github.butchecassidy.pocketbox.data.db.UserDataDao
import io.github.butchecassidy.pocketbox.data.scan.AudioFiles
import io.github.butchecassidy.pocketbox.data.scan.tags.RawTags
import io.github.butchecassidy.pocketbox.data.scan.tags.ReadOptions
import io.github.butchecassidy.pocketbox.domain.lyrics.LrcParser
import io.github.butchecassidy.pocketbox.domain.model.Lyrics
import io.github.butchecassidy.pocketbox.domain.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Lyrics only from the track's own tags or a sidecar `.lrc` file next to it. Never from the internet. */
class LyricsRepository(private val audioFiles: AudioFiles, private val dao: UserDataDao, private val io: CoroutineDispatcher) {
    // Quick taps on "earlier"/"later" each build on the value the previous tap saved.
    private val offsetLock = Mutex()

    /** The user's timing fix for a song in ms (positive: lyrics earlier); 0 when never changed. */
    fun offset(trackId: Long): Flow<Long> = dao.lyricOffset(trackId).map { it ?: 0L }

    /** Moves a song's lyrics timing by [deltaMs]; [reset] puts it back to the file's own timing. */
    suspend fun adjustOffset(trackId: Long, deltaMs: Long, reset: Boolean = false) = withContext(io) {
        offsetLock.withLock {
            val current = if (reset) 0L else dao.lyricOffsetOnce(trackId) ?: 0L
            val next = (current + deltaMs).coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
            if (next == 0L) dao.clearLyricOffset(trackId) else dao.setLyricOffset(trackId, next)
        }
    }

    /** Synced lyrics win over plain ones, whichever source they come from. */
    suspend fun load(track: Track): Lyrics? = withContext(io) {
        val sidecar = track.lyricsUri?.let { uri ->
            audioFiles.readSmallFile(uri.toUri(), MAX_LRC_BYTES)?.let { LrcParser.parse(decodeText(it)) }
        }
        if (sidecar is Lyrics.Synced) return@withContext sidecar
        val embedded = if (track.hasEmbeddedLyrics) {
            audioFiles.read(track.uri.toUri(), ReadOptions.LYRICS)?.tags?.first(RawTags.LYRICS)?.let(LrcParser::parse)
        } else {
            null
        }
        when {
            embedded is Lyrics.Synced -> embedded
            else -> sidecar ?: embedded
        }
    }

    companion object {
        private const val MAX_LRC_BYTES = 1024 * 1024
        const val MAX_OFFSET_MS = 30_000L

        /** UTF-8 (with or without BOM) or UTF-16 with BOM; anything else is read as Windows-1252. */
        internal fun decodeText(bytes: ByteArray): String {
            if (bytes.size >= 2) {
                val b0 = bytes[0].toInt() and 0xFF
                val b1 = bytes[1].toInt() and 0xFF
                if ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) return String(bytes, Charsets.UTF_16)
            }
            return try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (_: CharacterCodingException) {
                String(bytes, Charset.forName("windows-1252"))
            }
        }
    }
}
