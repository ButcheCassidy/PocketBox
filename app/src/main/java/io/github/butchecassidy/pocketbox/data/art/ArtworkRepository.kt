// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import io.github.butchecassidy.pocketbox.data.scan.AudioFiles
import io.github.butchecassidy.pocketbox.data.scan.tags.ReadOptions
import io.github.butchecassidy.pocketbox.domain.model.ArtRef
import io.github.butchecassidy.pocketbox.domain.model.ArtSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Album art: embedded pictures or cover.jpg/folder.jpg/front.png, decoded straight to the size it
 * is shown at. Two caches: an in-memory LRU (capped by bytes) and downscaled WebP thumbnails on disk
 * (capped at [DISK_LIMIT_BYTES], oldest removed first). Oversized or broken images are ignored.
 */
class ArtworkRepository(
    context: Context,
    private val audioFiles: AudioFiles,
    private val io: CoroutineDispatcher,
    private val scope: CoroutineScope,
) {
    private val diskDir = File(context.cacheDir, "art").apply { mkdirs() }
    private val inFlight = ConcurrentHashMap<String, Deferred<Bitmap?>>()
    private val writesSinceTrim = AtomicInteger()
    private val memory = object : LruCache<String, Bitmap>(memoryLimitBytes()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    /** Returns a cached bitmap immediately, or null if it has to be loaded. Safe on the main thread. */
    fun cached(art: ArtRef, sizePx: Int): Bitmap? = memory.get(key(art, bucket(sizePx)))

    /** Loads art at roughly [sizePx] (rounded up to a size bucket). Null when there is none or it's unusable. */
    suspend fun load(art: ArtRef, sizePx: Int): Bitmap? {
        val bucket = bucket(sizePx)
        val key = key(art, bucket)
        memory.get(key)?.let { return it }
        val job = inFlight.computeIfAbsent(key) { scope.async(io) { runCatching { loadUncached(art, bucket, key) }.getOrNull() } }
        return try {
            job.await()
        } finally {
            inFlight.remove(key, job)
        }
    }

    /** Raw picture bytes (for Media3 notification art), capped in size. */
    suspend fun bytes(art: ArtRef): ByteArray? = withContext(io) { extract(art) }

    /** Deletes every cached thumbnail, e.g. after the user removes a folder. */
    suspend fun clear() = withContext(io) {
        memory.evictAll()
        diskDir.listFiles()?.forEach { it.delete() }
    }

    private fun loadUncached(art: ArtRef, bucket: Int, key: String): Bitmap? {
        val file = File(diskDir, fileName(key))
        val fromDisk = if (file.isFile) decode(ByteBuffer.wrap(file.readBytes()), bucket) else null
        if (fromDisk != null) {
            file.setLastModified(System.currentTimeMillis())
            memory.put(key, fromDisk)
            return fromDisk
        }
        val bytes = extract(art) ?: return null
        val bitmap = decode(ByteBuffer.wrap(bytes), bucket) ?: return null
        memory.put(key, bitmap)
        writeThumbnail(file, bitmap)
        return bitmap
    }

    private fun extract(art: ArtRef): ByteArray? {
        val uri = Uri.parse(art.uri)
        return when (art.source) {
            ArtSource.EMBEDDED -> audioFiles.read(uri, ReadOptions.PICTURE)?.tags?.picture?.data
            ArtSource.FOLDER -> audioFiles.readSmallFile(uri, MAX_SOURCE_BYTES)
        }?.takeIf { it.size <= MAX_SOURCE_BYTES }
    }

    /** Decodes downscaled so the longest side is [bucket] px; partial or corrupt images return null. */
    private fun decode(buffer: ByteBuffer, bucket: Int): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(buffer)) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            if (width <= 0 || height <= 0) throw IllegalArgumentException("Empty image")
            val scale = minOf(1f, bucket.toFloat() / maxOf(width, height))
            decoder.setTargetSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setOnPartialImageListener { false }
        }
    }.getOrNull()

    private fun writeThumbnail(file: File, bitmap: Bitmap) {
        runCatching {
            val temp = File(diskDir, file.name + ".tmp")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, it) }
            if (!temp.renameTo(file)) temp.delete()
        }
        if (writesSinceTrim.incrementAndGet() >= TRIM_EVERY_WRITES) {
            writesSinceTrim.set(0)
            trimDisk()
        }
    }

    private fun trimDisk() {
        val files = diskDir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= DISK_LIMIT_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= DISK_TRIM_TARGET_BYTES) break
            total -= file.length()
            file.delete()
        }
    }

    private fun key(art: ArtRef, bucket: Int) = "${art.cacheKey}|$bucket"

    private fun fileName(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) } + ".webp"

    companion object {
        private const val MAX_SOURCE_BYTES = 16 * 1024 * 1024
        private const val DISK_LIMIT_BYTES = 128L * 1024 * 1024
        private const val DISK_TRIM_TARGET_BYTES = 96L * 1024 * 1024
        private const val TRIM_EVERY_WRITES = 25
        private const val WEBP_QUALITY = 85
        private const val MEMORY_CAP_BYTES = 64 * 1024 * 1024
        private val buckets = intArrayOf(96, 192, 384, 768, 1280)

        /** Rounds a requested size up to a fixed bucket so similar sizes share one cached copy. */
        fun bucket(sizePx: Int): Int = buckets.firstOrNull { it >= sizePx } ?: buckets.last()

        private fun memoryLimitBytes(): Int = minOf(Runtime.getRuntime().maxMemory() / 8, MEMORY_CAP_BYTES.toLong()).toInt()
    }
}
