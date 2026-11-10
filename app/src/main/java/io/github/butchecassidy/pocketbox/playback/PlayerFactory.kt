// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/** Builds ExoPlayer instances configured for local, offline music playback. */
object PlayerFactory {
    val musicAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    /**
     * @param bitPerfect when true, high-resolution audio is sent to Android as float PCM with no
     *   processing at all (no speed, pitch, ReplayGain or EQ), which is what bit-perfect USB output needs.
     * @param isMain the session's player manages audio focus; the crossfade helper never does.
     */
    fun create(
        context: Context,
        bitPerfect: Boolean,
        pauseOnDisconnect: Boolean,
        audioSessionId: Int,
        isMain: Boolean,
    ): ExoPlayer {
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(bitPerfect)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .build()
        }
        // Only content:// URIs from the Storage Access Framework can be opened: no HTTP data source exists.
        val dataSources = DataSource.Factory { ContentDataSource(context) }
        return ExoPlayer.Builder(context, renderers, DefaultMediaSourceFactory(dataSources, PocketExtractorsFactory()))
            .setAudioAttributes(musicAttributes, isMain)
            .setHandleAudioBecomingNoisy(isMain && pauseOnDisconnect)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setUsePlatformDiagnostics(false)
            .build()
            .apply { setAudioSessionId(audioSessionId) }
    }
}

/**
 * The main player's volume is the product of independent factors, so ReplayGain, crossfades and the
 * sleep-timer fade never fight over it. ReplayGain can only lower the volume, which also prevents clipping.
 */
class VolumeMixer(private val apply: (Float) -> Unit) {
    var replayGain = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            update()
        }
    var crossfade = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            update()
        }
    var sleepFade = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            update()
        }

    private fun update() = apply(replayGain * crossfade * sleepFade)
}
