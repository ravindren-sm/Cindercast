package io.github.ravindren_sm.openstream.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/**
 * App theme: the prototype's dark TV system mapped onto the Compose for TV color scheme and
 * typography (mapping table: docs/OPENSTREAM_INTAKE.md, "Worth carrying over").
 *
 *   type.display      -> displaySmall   (48 sp)
 *   type.titleLarge   -> headlineMedium (36 sp)
 *   type.titleMedium  -> headlineSmall  (32 sp)
 *   type.bodyLarge    -> titleMedium    (28 sp)
 *   type.body         -> bodyLarge      (24 sp)
 * Other styles keep their defaults and should not be used for on-screen text.
 */
private val OpenStreamColors = darkColorScheme(
    primary = Tokens.AccentPrimary,
    onPrimary = Tokens.BgCanvas,
    background = Tokens.BgCanvas,
    onBackground = Tokens.TextPrimary,
    surface = Tokens.Surface1,
    onSurface = Tokens.TextPrimary,
    surfaceVariant = Tokens.Surface2,
    onSurfaceVariant = Tokens.TextSecondary,
    error = Tokens.StatusError,
    onError = Tokens.BgCanvas,
    border = Tokens.FocusRing,
)

private val OpenStreamTypography = Typography(
    displaySmall = Tokens.Display,
    headlineMedium = Tokens.TitleLarge,
    headlineSmall = Tokens.TitleMedium,
    titleMedium = Tokens.BodyLarge,
    bodyLarge = Tokens.Body,
)

/** Width of the canvas the design tokens are written for (the prototype's 1920 x 1080). */
const val DESIGN_WIDTH_PX = 1920f

/**
 * Pixels per dp so that 1 dp = 1 pixel of the design canvas, on any screen.
 *
 * Why: the tokens (48 px margins, 24 px body text, 3 px focus ring) are pixel values for a
 * 1920-pixel-wide screen. Android TV reports 320 dpi for a 1080p interface, which makes 1 dp two
 * pixels (a 960 x 540 dp screen), so unscaled token values come out twice too big. Measured on the
 * Fire TV Stick 4K Plus: 1920x1080 interface at 320 dpi; at that size the tab bar overflowed.
 */
fun designDensity(screenWidthPx: Float): Float = screenWidthPx / DESIGN_WIDTH_PX

@Composable
fun OpenStreamTheme(content: @Composable () -> Unit) {
    val real = LocalDensity.current
    val widthPx = LocalConfiguration.current.screenWidthDp * real.density
    // User font scale is kept (accessibility); only the pixel grid is normalized.
    val design = Density(density = designDensity(widthPx), fontScale = real.fontScale)
    CompositionLocalProvider(LocalDensity provides design) {
        MaterialTheme(colorScheme = OpenStreamColors, typography = OpenStreamTypography, content = content)
    }
}
