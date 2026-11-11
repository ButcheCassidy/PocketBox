// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.playback

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.github.butchecassidy.pocketbox.data.art.ArtworkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * Gives Media3 album art for the notification, lock screen and connected devices, from Pocket Box's
 * own art cache. Only Pocket Box's private art URIs are understood; nothing is ever fetched.
 */
class ArtworkBitmapLoader(
    private val artwork: ArtworkRepository,
    private val scope: CoroutineScope,
) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val bitmap = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(data))) { decoder, info, _ ->
                val scale = minOf(1f, SIZE_PX.toFloat() / maxOf(info.size.width, info.size.height, 1))
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
        }.getOrNull()
        return if (bitmap != null) Futures.immediateFuture(bitmap) else Futures.immediateFailedFuture(IllegalArgumentException("Undecodable art"))
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val art = MediaItems.parseArtworkUri(uri)
            ?: return Futures.immediateFailedFuture(IllegalArgumentException("Unsupported art URI"))
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            val bitmap = artwork.load(art, SIZE_PX)
            if (bitmap != null) future.set(bitmap) else future.setException(IllegalStateException("No art"))
        }
        return future
    }

    private companion object {
        const val SIZE_PX = 768
    }
}
