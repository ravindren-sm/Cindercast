package io.github.ravindren_sm.openstream.ui.theme

import androidx.compose.runtime.Composable
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

@Composable
fun OpenStreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OpenStreamColors, typography = OpenStreamTypography, content = content)
}
