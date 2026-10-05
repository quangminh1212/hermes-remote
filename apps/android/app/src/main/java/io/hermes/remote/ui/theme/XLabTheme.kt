package io.hermes.remote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * XLab colour tokens.
 *
 * Mirrors `tailwind.config.js` in the XLab web project so the phone app and the
 * web surfaces share one palette. Values are duplicated rather than shared
 * because the two runtimes have no common build step; keeping them in one
 * clearly-named object makes drift obvious and greppable.
 *
 * Contrast note: `textPrimary` on `bgPrimary` is ~13:1 and `textSecondary` is
 * ~8:1, both well past WCAG AA for body text. `textTertiary` is reserved for
 * labels and metadata, not for content the user must read to act.
 */
object XLabColors {
    // Brand
    val Primary = Color(0xFF00A19A)
    val PrimaryDark = Color(0xFF0D9488)
    val PrimaryLight = Color(0xFF33D6D6)
    val Secondary = Color(0xFF37C88F)

    // Dark surfaces (slate ramp)
    val BgPrimary = Color(0xFF1E293B)
    val BgSecondary = Color(0xFF334155)
    val BgTertiary = Color(0xFF475569)
    val BgElevated = Color(0xFF64748B)

    // Text
    val TextPrimary = Color(0xFFF7F9FB)
    val TextSecondary = Color(0xFFC9D1D9)
    val TextTertiary = Color(0xFF8B949E)

    // Borders
    val BorderSubtle = Color(0xFF475569)
    val Border = Color(0xFF64748B)

    // Status
    val Success = Color(0xFF3FB950)
    val Warning = Color(0xFFD29922)
    val Error = Color(0xFFF85149)
}

/**
 * Dark scheme built from the XLab tokens.
 *
 * The app is intentionally dark-only: the voice/chat surface is used in low
 * light and the brand system is defined against dark surfaces, so a light
 * variant would be a second design to maintain for no user benefit.
 */
private val XLabDarkScheme = darkColorScheme(
    primary = XLabColors.Primary,
    onPrimary = Color.White,
    primaryContainer = XLabColors.PrimaryDark,
    onPrimaryContainer = Color.White,

    secondary = XLabColors.Secondary,
    onSecondary = Color.White,

    background = XLabColors.BgPrimary,
    onBackground = XLabColors.TextPrimary,

    surface = XLabColors.BgSecondary,
    onSurface = XLabColors.TextPrimary,
    surfaceVariant = XLabColors.BgTertiary,
    onSurfaceVariant = XLabColors.TextSecondary,

    outline = XLabColors.Border,
    outlineVariant = XLabColors.BorderSubtle,

    error = XLabColors.Error,
    onError = Color.White,
)

/**
 * App theme.
 *
 * [darkTheme] is accepted but ignored — see [XLabDarkScheme]. The parameter is
 * kept so a future light scheme is a one-line change rather than a call-site
 * refactor.
 */
@Composable
fun XLabTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = XLabDarkScheme,
        content = content,
    )
}
