package io.hermes.remote.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The app's colour tokens, in two sets.
 *
 * These are an interface rather than an object of constants because the values
 * now depend on the active mode. Read them through [XLabTheme.colors] (or the
 * `LocalXLabColors.current` that backs it), never from a top-level name — a
 * constant cannot change with the theme, which is exactly the bug this shape
 * prevents.
 *
 * The dark set follows a terminal, not a web app: the base is neutral black
 * rather than slate. The previous slate ramp (#1E293B and up) read as muddy
 * grey-blue, and because the reply bubble sat on #475569 it looked like wet
 * cement. Neutral greys keep the teal as the only hue in the chrome, which is
 * what makes a terminal feel calm.
 */
interface XLabPalette {
    // Brand. Constant across modes: it is the identity, not a surface.
    val primary: Color
    val primaryDark: Color
    val primaryLight: Color
    val secondary: Color

    // Surfaces, darkest to lightest.
    val bgPrimary: Color
    val bgSecondary: Color
    val bgTertiary: Color
    val bgElevated: Color

    // Text, highest to lowest emphasis.
    val textPrimary: Color
    val textSecondary: Color
    val textTertiary: Color

    // Separators.
    val borderSubtle: Color
    val border: Color
    val borderStrong: Color

    // Status.
    val success: Color
    val warning: Color
    val error: Color

    /** The user's own message bubble. */
    val userBubble: Color
    /** Text on [userBubble]. */
    val onUserBubble: Color

    /**
     * Hermes' reply bubble.
     *
     * A surface *near* the background rather than far from it: in a transcript
     * the reply is the default case, so it should read as plain text on the
     * page. Only the user's turn is highlighted. This is the reverse of the
     * usual chat convention, and it is deliberate — most of the screen is the
     * assistant, so giving it the loudest surface would invert the hierarchy.
     */
    val assistantBubble: Color
    /** Text on [assistantBubble]. */
    val onAssistantBubble: Color

    /** Error bubble background. */
    val errorBubble: Color

    /** Tinted wash behind the "from Assistant" annotation. */
    val assistantWash: Color

    /** The glyph inside the brand mark. */
    val onBrand: Color
}

/**
 * Terminal-style dark: neutral black base, teal as the only hue.
 *
 * Internal rather than private so unit tests can measure the real values. A
 * test holding its own copy of the palette would keep passing while the app's
 * colours drifted, which is worse than no test.
 */
internal object UnixDark : XLabPalette {
    override val primary = Color(0xFF00A19A)
    override val primaryDark = Color(0xFF0D9488)
    override val primaryLight = Color(0xFF2FD4CC)
    override val secondary = Color(0xFF37C88F)

    // Near-black, cmd-style. #0C0C0C is dark enough to disappear on an OLED
    // panel while staying just above pure black, so the bubble at #161616 is
    // still distinguishable from the page.
    override val bgPrimary = Color(0xFF0C0C0C)
    override val bgSecondary = Color(0xFF161616)
    override val bgTertiary = Color(0xFF1F1F1F)
    override val bgElevated = Color(0xFF2A2A2A)

    override val textPrimary = Color(0xFFE6E6E6)
    override val textSecondary = Color(0xFFB8B8B8)
    override val textTertiary = Color(0xFF8A8A8A)

    override val borderSubtle = Color(0xFF2A2A2A)
    override val border = Color(0xFF3A3A3A)
    override val borderStrong = Color(0xFF4D4D4D)

    override val success = Color(0xFF3FB950)
    override val warning = Color(0xFFD29922)
    override val error = Color(0xFFF85149)

    override val userBubble = Color(0xFF00403D)
    override val onUserBubble = Color(0xFFD7F7F5)

    override val assistantBubble = Color(0xFF161616)
    override val onAssistantBubble = Color(0xFFE6E6E6)

    override val errorBubble = Color(0xFF2A1416)
    override val assistantWash = Color(0xFF0F2A28)
    override val onBrand = Color(0xFFFFFFFF)
}

/**
 * VS Code Light: white page, near-black text, same teal.
 *
 * Internal for the same reason as [UnixDark] — tests measure the real values.
 */
internal object DaylightLight : XLabPalette {
    override val primary = Color(0xFF00857F)
    override val primaryDark = Color(0xFF006B66)
    override val primaryLight = Color(0xFF00A19A)
    override val secondary = Color(0xFF1A7F5A)

    override val bgPrimary = Color(0xFFFFFFFF)
    override val bgSecondary = Color(0xFFF6F8FA)
    override val bgTertiary = Color(0xFFEAEEF2)
    override val bgElevated = Color(0xFFFFFFFF)

    override val textPrimary = Color(0xFF1F2328)
    override val textSecondary = Color(0xFF4A5259)
    override val textTertiary = Color(0xFF6E7781)

    override val borderSubtle = Color(0xFFD8DEE4)
    override val border = Color(0xFFBDC4CC)
    override val borderStrong = Color(0xFF8C959F)

    override val success = Color(0xFF1A7F37)
    override val warning = Color(0xFF9A6700)
    override val error = Color(0xFFCF222E)

    // A filled teal bubble with white text is the one place light mode can
    // carry a strong surface, because it is the minority of the screen.
    override val userBubble = Color(0xFF00857F)
    override val onUserBubble = Color(0xFFFFFFFF)

    override val assistantBubble = Color(0xFFF6F8FA)
    override val onAssistantBubble = Color(0xFF1F2328)

    override val errorBubble = Color(0xFFFFEBE9)
    override val assistantWash = Color(0xFFE6F5F4)
    override val onBrand = Color(0xFFFFFFFF)
}

/** The active palette. Defaults to dark so a preview render never looks broken. */
val LocalXLabColors = compositionLocalOf<XLabPalette> { UnixDark }

/** Corner radii, matching XLab's `borderRadius` scale. */
private val XLabShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * A tighter radius scale for the chat screen, in the spirit of an editor UI.
 *
 * An editor stays legible because its chrome is flat and near-square: a tab, a
 * status bar, a panel header sit at 2–6dp, and only genuine callouts get more.
 * These are small on purpose — [chip] and [control] should read as UI, not as
 * bubbles.
 */
object Dense {
    /** Status bar, panel headers, text fields. */
    val control = 6.dp

    /** Message bubbles: rounded enough to read as speech, not as a card. */
    val bubble = 8.dp

    /** Chips and tags. Small, so a row of them does not become a blob. */
    val chip = 4.dp

    /** Thickness of the leading rule on banners and callouts. */
    val rule = 2.dp
}

/**
 * Typography with explicit line heights.
 *
 * The default Material scale is tuned for Material's own look; these keep the
 * feel of a code editor where body copy is roomy and labels are compact.
 * Only the styles the app actually uses are overridden.
 */
private val XLabTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(letterSpacing = 0.3.sp),
        labelSmall = base.labelSmall.copy(letterSpacing = 0.3.sp),
        bodyLarge = base.bodyLarge.copy(lineHeight = 23.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
        bodySmall = base.bodySmall.copy(lineHeight = 18.sp),
    )
}

/** Monospace for URLs, keys and agent ids, which must not be re-flowed. */
val XLabMono: FontFamily = FontFamily.Monospace

/** A compact code/label style for technical values shown in the UI. */
val XLabCodeStyle: TextStyle
    get() = TextStyle(
        fontFamily = XLabMono,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

/** Build the Material scheme for a palette. */
private fun schemeFor(p: XLabPalette, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = p.primary,
        onPrimary = p.onBrand,
        primaryContainer = p.primaryDark,
        onPrimaryContainer = p.onBrand,

        secondary = p.secondary,
        onSecondary = p.onBrand,
        secondaryContainer = p.assistantBubble,
        onSecondaryContainer = p.textPrimary,

        tertiary = p.primaryLight,
        onTertiary = p.bgPrimary,
        tertiaryContainer = p.bgTertiary,
        onTertiaryContainer = p.textPrimary,

        background = p.bgPrimary,
        onBackground = p.textPrimary,

        surface = p.bgSecondary,
        onSurface = p.textPrimary,
        surfaceVariant = p.bgTertiary,
        onSurfaceVariant = p.textSecondary,

        outline = p.border,
        outlineVariant = p.borderSubtle,

        error = p.error,
        onError = p.onBrand,
        errorContainer = p.errorBubble,
        onErrorContainer = p.textPrimary,
    )
} else {
    lightColorScheme(
        primary = p.primary,
        onPrimary = p.onBrand,
        primaryContainer = p.primaryLight,
        onPrimaryContainer = p.onBrand,

        secondary = p.secondary,
        onSecondary = p.onBrand,
        secondaryContainer = p.assistantBubble,
        onSecondaryContainer = p.textPrimary,

        tertiary = p.primaryDark,
        onTertiary = p.onBrand,
        tertiaryContainer = p.bgTertiary,
        onTertiaryContainer = p.textPrimary,

        background = p.bgPrimary,
        onBackground = p.textPrimary,

        surface = p.bgPrimary,
        onSurface = p.textPrimary,
        surfaceVariant = p.bgTertiary,
        onSurfaceVariant = p.textSecondary,

        outline = p.border,
        outlineVariant = p.borderSubtle,

        error = p.error,
        onError = p.onBrand,
        errorContainer = p.errorBubble,
        onErrorContainer = p.textPrimary,
    )
}

/**
 * The brand mark's gradient, shared by the logo and the header rule.
 *
 * Always the brand teal, in both modes: it identifies the product rather than
 * describing a surface, so it does not follow the theme.
 */
val XLabBrandGradient: Brush
    @Composable get() = Brush.linearGradient(
        listOf(LocalXLabColors.current.primary, LocalXLabColors.current.primaryDark),
    )

/**
 * App theme.
 *
 * @param dark when true use the terminal-dark palette, else the light one.
 *   Callers resolve "follow the system" before this — see [ThemeMode] — so the
 *   theme itself only ever deals in a settled boolean.
 */
@Composable
fun XLabTheme(
    dark: Boolean = true,
    content: @Composable () -> Unit,
) {
    val palette: XLabPalette = if (dark) UnixDark else DaylightLight
    androidx.compose.runtime.CompositionLocalProvider(LocalXLabColors provides palette) {
        MaterialTheme(
            colorScheme = schemeFor(palette, dark),
            shapes = XLabShapes,
            typography = XLabTypography,
            content = content,
        )
    }
}

/**
 * The active palette, as `XLabTheme.colors`.
 *
 * A named accessor rather than a bare composition local so that reading a token
 * looks the same everywhere and the dependency on the Compose runtime lives in
 * one greppable place.
 */
val XLabThemeColors: XLabPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalXLabColors.current
