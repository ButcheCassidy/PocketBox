// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OutputKind(val label: String) {
    SPEAKER("Phone speaker"),
    WIRED("Wired headphones"),
    USB("USB audio"),
    BLUETOOTH("Bluetooth"),
    OTHER("Other output"),
}

/** Bit-perfect USB output: what was asked for, and whether Android confirmed it. */
sealed interface BitPerfectStatus {
    data object Off : BitPerfectStatus
    data object NoUsbDevice : BitPerfectStatus
    data class Unsupported(val reason: String) : BitPerfectStatus
    data class Active(val sampleRateHz: Int, val encoding: String) : BitPerfectStatus
}

/**
 * What's reaching the speaker, headphones or DAC right now. Only facts Android exposes publicly
 * (and without extra permissions) are shown; anything else is null and labelled as unknown.
 *
 * @property appSampleRateHz / [appEncoding] what Pocket Box hands to Android (Media3's AudioTrack).
 * @property mixerSampleRateHz the rate Android mixes at for this output, when Android reveals it.
 */
data class OutputInfo(
    val kind: OutputKind = OutputKind.SPEAKER,
    val deviceName: String = OutputKind.SPEAKER.label,
    val appSampleRateHz: Int? = null,
    val appEncoding: String? = null,
    val mixerSampleRateHz: Int? = null,
    val bitPerfect: BitPerfectStatus = BitPerfectStatus.Off,
) {
    val bluetoothCodecNote: String? get() = if (kind == OutputKind.BLUETOOTH) "Bluetooth: codec not exposed by Android" else null

    /** True when Android's mixer runs at a different rate than the song's own. */
    fun isResampled(sourceRateHz: Int?): Boolean {
        if (sourceRateHz == null) return false
        val effective = (bitPerfect as? BitPerfectStatus.Active)?.sampleRateHz ?: mixerSampleRateHz ?: appSampleRateHz ?: return false
        return effective != sourceRateHz
    }
}

/**
 * Watches the audio route (headphones plugged in, Bluetooth connected, USB DAC attached) and the
 * format Media3 opens its AudioTrack with, and manages Android 14's bit-perfect USB mode.
 */
class OutputMonitor(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val platformAttributes = PlayerFactory.musicAttributes.audioAttributesV21.audioAttributes
    private val main = Handler(Looper.getMainLooper())
    private val _info = MutableStateFlow(OutputInfo())
    private val _reopenRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var bitPerfectRequested = false
    private var trackConfig: AudioSink.AudioTrackConfig? = null

    val info: StateFlow<OutputInfo> = _info.asStateFlow()

    /** Emitted when the player must reopen its audio output so a new bit-perfect format takes effect. */
    val reopenRequests: SharedFlow<Unit> = _reopenRequests.asSharedFlow()

    val analyticsListener = object : AnalyticsListener {
        override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
            trackConfig = audioTrackConfig
            refresh()
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
            },
            main,
        )
        refresh()
    }

    fun setBitPerfectRequested(requested: Boolean) {
        if (requested == bitPerfectRequested) return
        bitPerfectRequested = requested
        if (!requested) usbDevice()?.let { runCatching { audioManager.clearPreferredMixerAttributes(platformAttributes, it) } }
        refresh()
    }

    private fun currentDevice(): AudioDeviceInfo? =
        runCatching { audioManager.getAudioDevicesForAttributes(platformAttributes).firstOrNull() }.getOrNull()

    private fun usbDevice(): AudioDeviceInfo? = currentDevice()?.takeIf { kindOf(it) == OutputKind.USB }

    /** Recomputes everything; called on route changes and when Media3 opens a new AudioTrack. */
    fun refresh() {
        val device = currentDevice()
        val kind = device?.let(::kindOf) ?: OutputKind.SPEAKER
        val name = when {
            device == null || kind == OutputKind.SPEAKER -> OutputKind.SPEAKER.label
            else -> device.productName?.toString()?.takeIf { it.isNotBlank() } ?: kind.label
        }
        val config = trackConfig
        val bitPerfect = updateBitPerfect(device, kind, config)
        val mixerRate = when (kind) {
            // The USB path's mixer rate is only known when bit-perfect pins it.
            OutputKind.USB -> null
            else -> audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        }
        _info.value = OutputInfo(
            kind = kind,
            deviceName = name,
            appSampleRateHz = config?.sampleRate,
            appEncoding = config?.encoding?.let(::encodingLabel),
            mixerSampleRateHz = mixerRate,
            bitPerfect = bitPerfect,
        )
    }

    /** Asks Android for a bit-perfect mixer matching the current song's format, and reports the outcome. */
    private fun updateBitPerfect(device: AudioDeviceInfo?, kind: OutputKind, config: AudioSink.AudioTrackConfig?): BitPerfectStatus {
        if (!bitPerfectRequested) return BitPerfectStatus.Off
        if (device == null || kind != OutputKind.USB) return BitPerfectStatus.NoUsbDevice
        if (config == null) return BitPerfectStatus.Unsupported("Start playing a song to set up bit-perfect output")
        val channels = Integer.bitCount(config.channelConfig)
        val options = runCatching { audioManager.getSupportedMixerAttributes(device) }.getOrDefault(emptyList())
            .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
        val match = options.firstOrNull {
            it.format.sampleRate == config.sampleRate && it.format.encoding == config.encoding && it.format.channelCount == channels
        } ?: return BitPerfectStatus.Unsupported(
            if (options.isEmpty()) "This USB device doesn't offer bit-perfect output"
            else "This USB device can't play ${config.sampleRate / 1000.0} kHz ${encodingLabel(config.encoding)} bit-perfect",
        )
        val current = runCatching { audioManager.getPreferredMixerAttributes(platformAttributes, device) }.getOrNull()
        if (current != match) {
            val accepted = runCatching { audioManager.setPreferredMixerAttributes(platformAttributes, device, match) }.getOrDefault(false)
            if (!accepted) return BitPerfectStatus.Unsupported("Android declined the bit-perfect request")
            _reopenRequests.tryEmit(Unit)
        }
        return BitPerfectStatus.Active(match.format.sampleRate, encodingLabel(match.format.encoding))
    }

    companion object {
        val bluetoothTypes = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )

        fun kindOf(device: AudioDeviceInfo): OutputKind = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
            -> OutputKind.SPEAKER
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_LINE_ANALOG -> OutputKind.WIRED
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> OutputKind.USB
            in bluetoothTypes -> OutputKind.BLUETOOTH
            else -> OutputKind.OTHER
        }

        fun encodingLabel(encoding: Int): String = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit"
            AudioFormat.ENCODING_PCM_32BIT -> "32-bit"
            AudioFormat.ENCODING_PCM_FLOAT -> "32-bit float"
            AudioFormat.ENCODING_PCM_8BIT -> "8-bit"
            else -> "encoded"
        }
    }
}
