package io.github.ravindren_sm.openstream.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Poster images, sized for a 32-bit stick with little memory: decoded as RGB_565 (2 bytes a pixel),
 * downsampled to about the size they are shown at, kept in a byte-bounded LRU cache, and fetched at most
 * [MAX_CONCURRENT] at a time so a fast scroll does not flood the host. A failed fetch is not cached, so
 * the placeholder stays and a later visit tries again. Poster addresses are never logged.
 */
object PosterLoader {
    private const val MAX_CONCURRENT = 4
    private const val CACHE_BYTES = 24 * 1024 * 1024
    private const val MAX_DOWNLOAD_BYTES = 4 * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
    private val gate = Semaphore(MAX_CONCURRENT)
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 2
    }

    fun cached(url: String): ImageBitmap? = cache.get(url)

    /** Drops every cached poster (Settings: clear cache). Posters are fetched again when next shown. */
    fun clearCache() = cache.evictAll()

    /** Bytes held by the cache now (RGB_565 pixels). */
    fun cacheBytes(): Int = cache.size()

    /** Fetches and decodes [url] at about [targetWidthPx] wide; null on any failure. */
    suspend fun load(url: String, targetWidthPx: Int): ImageBitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                cache.get(url)?.let { return@withPermit it }
                val bytes = try {
                    client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                        if (!r.isSuccessful) return@withPermit null
                        r.body?.byteStream()?.use { readCapped(it, MAX_DOWNLOAD_BYTES) }
                    }
                } catch (_: Exception) { null } ?: return@withPermit null
                decode(bytes, targetWidthPx)?.asImageBitmap()?.also { cache.put(url, it) }
            }
        }
    }

    internal fun decode(bytes: ByteArray, targetWidthPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidthPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    /** Reads at most [max] bytes; null if the body is larger (not a poster). */
    private fun readCapped(input: java.io.InputStream, max: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) return null
        }
        return out.toByteArray()
    }
}

/** A poster that fills its parent, with a flat placeholder until (or unless) the image arrives. */
@Composable
fun Poster(url: String?, modifier: Modifier = Modifier, targetWidthPx: Int = 400) {
    var image by remember(url) { mutableStateOf(url?.let { PosterLoader.cached(it) }) }
    LaunchedEffect(url) { if (image == null && url != null) image = PosterLoader.load(url, targetWidthPx) }
    Box(modifier.background(Tokens.SurfaceEmpty)) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}
