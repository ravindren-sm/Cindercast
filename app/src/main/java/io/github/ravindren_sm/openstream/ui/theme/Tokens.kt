package io.github.ravindren_sm.openstream.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Design tokens for the 10-foot UI.
 *
 * Source: the OpenStream design prototype, commit 01bcc7990f67, src/design/tokens.ts
 * (docs/OPENSTREAM_INTAKE.md). Values are copied as data; names follow the prototype so the
 * two stay comparable. Sizes assume a 1920x1080 canvas at 1 px = 1 dp / 1 sp.
 *
 * Measured contrast (WCAG ratio, checked by ContrastTest):
 *   text.primary on canvas 17.8, on surface.1 16.2, on surface.3 12.1
 *   text.secondary on canvas 9.1, surface.1 8.3, surface.2 7.3, surface.3 6.2
 * The prototype's "secondary text above 8.5:1" holds on the canvas only; every pair is still
 * above the 4.5:1 minimum for body text.
 */
object Tokens {
    // Color: dark TV system, about 60% canvas, 30% surfaces, 10% accent.
    val BgCanvas = Color(0xFF0B0E14)
    val Surface1 = Color(0xFF141923)       // resting cards, rails, settings panes
    val Surface2 = Color(0xFF1D2432)       // selected categories, populated cells
    val Surface3 = Color(0xFF283142)       // focused row/card
    val SurfaceEmpty = Color(0xFF10141C)   // "No information" cells, skeletons
    val FocusRing = Color(0xFFF8FAFC)      // 3 dp, visible at 3 m
    val AccentPrimary = Color(0xFF38BDF8)  // nav underline, progress, "now" line
    val TextPrimary = Color(0xFFF3F6FA)
    val TextSecondary = Color(0xFFA6B3C6)
    val StatusSuccess = Color(0xFF34D399)  // always paired with a text label
    val StatusWarning = Color(0xFFFBBF24)
    val StatusError = Color(0xFFF87171)

    // Type scale: nothing below 24 sp for body text.
    val Display = TextStyle(fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold)
    val TitleLarge = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
    val TitleMedium = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold)
    val BodyLarge = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Normal)
    /** Channel numbers, clocks, timecodes: tabular figures so digits do not jitter. */
    val Mono = TextStyle(
        fontFamily = FontFamily.Monospace, fontSize = 24.sp, lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum",
    )
}

/** 8 dp grid and TV overscan margins (2.5% of 1920 x 1080). */
object Spacing {
    val SafeHorizontal = 48.dp
    val SafeVertical = 27.dp
    val S1 = 8.dp    // icon-to-label gap, badge spacing
    val S2 = 16.dp   // card padding, dense list gap
    val S3 = 24.dp   // grid gutters, button groups
    val S4 = 32.dp   // shelf spacing, column gutters
}

object Radii {
    val Sm = 8.dp    // badges, guide cells
    val Md = 12.dp   // cards, buttons
    val Lg = 16.dp   // modals, panels
}

/** Focus visuals shared by every focusable component. */
object FocusStyle {
    val RingWidth = 3.dp
    const val CardScale = 1.06f
}

/** WCAG 2.x contrast ratio between two opaque 0xRRGGBB colors. Pure math, unit-tested. */
object Contrast {
    fun ratio(a: Long, b: Long): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(rgb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((rgb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
