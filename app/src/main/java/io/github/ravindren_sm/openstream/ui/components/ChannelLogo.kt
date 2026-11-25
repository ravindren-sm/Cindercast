package io.github.ravindren_sm.openstream.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.ChannelQuality
import io.github.ravindren_sm.openstream.ui.theme.Radii
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import java.util.Collections

/** Addresses whose logo failed to load this run (bounded): a missing logo is not asked for again on every scroll. */
private val failedLogos: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet())
private const val MAX_FAILED_LOGOS = 3000

/**
 * A channel logo in the box the caller sizes, or a flat neutral placeholder while it loads, when there is none, or when it
 * cannot be fetched. Loaded by [PosterLoader] (small RGB_565 decode, shared byte-bounded cache, four fetches at a time) at
 * about [targetWidthPx], so a row of logos costs a few kilobytes each. Loading runs off the main thread and ends when the row
 * scrolls away, so scrolling never waits for a logo.
 */
@Composable
fun ChannelLogo(url: String?, modifier: Modifier = Modifier, targetWidthPx: Int = 120) {
    var image: ImageBitmap? by remember(url) { mutableStateOf(url?.let { PosterLoader.cached(it) }) }
    LaunchedEffect(url) {
        if (image != null || url == null || url in failedLogos) return@LaunchedEffect
        val loaded = PosterLoader.load(url, targetWidthPx)
        if (loaded != null) image = loaded else synchronized(failedLogos) {
            if (failedLogos.size >= MAX_FAILED_LOGOS) failedLogos.remove(failedLogos.first())
            failedLogos.add(url)
        }
    }
    Box(modifier.background(Tokens.SurfaceEmpty, RoundedCornerShape(Radii.Sm))) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp)) }
    }
}

/** The small outlined HD / FHD / UHD / 4K tag shown beside a channel name. */
@Composable
fun QualityBadge(quality: ChannelQuality, modifier: Modifier = Modifier) {
    Text(
        quality.label, style = Tokens.Body, color = Tokens.AccentPrimary, maxLines = 1,
        modifier = modifier
            .border(2.dp, Tokens.AccentPrimary, RoundedCornerShape(Radii.Sm))
            .padding(horizontal = 8.dp, vertical = 0.dp),
    )
}
