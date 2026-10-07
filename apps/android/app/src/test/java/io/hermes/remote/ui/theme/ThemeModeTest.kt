package io.hermes.remote.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the theme's two contracts: how a [ThemeMode] resolves to dark/light, and
 * whether each palette is actually legible and actually different.
 *
 * The contrast check is the point of this file. A palette is easy to break by
 * nudging a hex value — the app still compiles and still renders, it just
 * becomes unreadable. That failure is invisible in a unit test unless the test
 * measures it, and invisible in a screenshot unless someone looks closely at
 * the right screen.
 */
class ThemeModeTest {

    @Test
    fun `system follows the os setting`() {
        assertTrue(ThemeMode.SYSTEM.resolveDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.resolveDark(systemDark = false))
    }

    @Test
    fun `explicit modes ignore the os setting`() {
        assertTrue(ThemeMode.DARK.resolveDark(systemDark = false))
        assertTrue(ThemeMode.DARK.resolveDark(systemDark = true))
        assertFalse(ThemeMode.LIGHT.resolveDark(systemDark = true))
        assertFalse(ThemeMode.LIGHT.resolveDark(systemDark = false))
    }

    @Test
    fun `unknown or missing persisted value falls back to system`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId("nonsense"))
    }

    @Test
    fun `persisted value round-trips for every mode`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromId(mode.name))
        }
    }

    @Test
    fun `persisted value is accepted case-insensitively`() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromId("dark"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromId("Light"))
    }
}

/**
 * Contrast and structure checks on both palettes.
 *
 * Uses the WCAG relative-luminance formula. The bar is 3.0:1 for large text and
 * non-text UI, 4.5:1 for body copy. Both palettes must clear the body-text bar
 * on their primary surface, because that is where the transcript is read.
 */
class PaletteTest {

    private val dark: XLabPalette = UnixDark
    private val light: XLabPalette = DaylightLight

    /** WCAG 2.x relative luminance. */
    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    /** WCAG contrast ratio, always >= 1. */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun `dark body text clears the wcag body bar on its page`() {
        assertTrue(
            "textPrimary on bgPrimary was ${contrast(dark.textPrimary, dark.bgPrimary)}",
            contrast(dark.textPrimary, dark.bgPrimary) >= 4.5,
        )
    }

    @Test
    fun `light body text clears the wcag body bar on its page`() {
        assertTrue(
            "textPrimary on bgPrimary was ${contrast(light.textPrimary, light.bgPrimary)}",
            contrast(light.textPrimary, light.bgPrimary) >= 4.5,
        )
    }

    @Test
    fun `reply text is legible inside the reply bubble in both modes`() {
        assertTrue(
            "dark reply was ${contrast(dark.onAssistantBubble, dark.assistantBubble)}",
            contrast(dark.onAssistantBubble, dark.assistantBubble) >= 4.5,
        )
        assertTrue(
            "light reply was ${contrast(light.onAssistantBubble, light.assistantBubble)}",
            contrast(light.onAssistantBubble, light.assistantBubble) >= 4.5,
        )
    }

    @Test
    fun `user text is legible inside the user bubble in both modes`() {
        assertTrue(
            "dark user was ${contrast(dark.onUserBubble, dark.userBubble)}",
            contrast(dark.onUserBubble, dark.userBubble) >= 4.5,
        )
        assertTrue(
            "light user was ${contrast(light.onUserBubble, light.userBubble)}",
            contrast(light.onUserBubble, light.userBubble) >= 4.5,
        )
    }

    @Test
    fun `secondary text stays readable, not decorative`() {
        // 3.0 rather than 4.5: secondary text is never the only carrier of a
        // meaning, so it may sit at the large-text bar.
        assertTrue(contrast(dark.textSecondary, dark.bgPrimary) >= 3.0)
        assertTrue(contrast(light.textSecondary, light.bgPrimary) >= 3.0)
    }

    @Test
    fun `the reply bubble is distinguishable from the page in both modes`() {
        // A reply bubble that is exactly the page colour would make the
        // transcript an undifferentiated wall of text.
        assertTrue(
            "dark bubble/page delta was ${
                contrast(dark.assistantBubble, dark.bgPrimary)
            }",
            contrast(dark.assistantBubble, dark.bgPrimary) > 1.0,
        )
        assertTrue(
            "light bubble/page delta was ${
                contrast(light.assistantBubble, light.bgPrimary)
            }",
            contrast(light.assistantBubble, light.bgPrimary) > 1.0,
        )
    }

    @Test
    fun `user and reply bubbles are never the same surface`() {
        // Otherwise side-alignment would be the only thing separating speakers.
        assertTrue(dark.userBubble != dark.assistantBubble)
        assertTrue(light.userBubble != light.assistantBubble)
    }

    @Test
    fun `dark base really is darker than the old slate it replaced`() {
        // Regression guard: #1E293B was the muddy blue-grey the redesign set
        // out to remove. Asserting the new base is darker keeps someone from
        // drifting back to it.
        val oldSlate = Color(0xFF1E293B)
        assertTrue(
            "dark bgPrimary luminance ${luminance(dark.bgPrimary)} vs old slate " +
                "${luminance(oldSlate)}",
            luminance(dark.bgPrimary) < luminance(oldSlate),
        )
    }

    @Test
    fun `dark palette is neutral, not blue-tinted`() {
        // The slate ramp read as muddy because its blue channel dominated. A
        // neutral ramp keeps the channels close together.
        listOf(dark.bgPrimary, dark.bgSecondary, dark.bgTertiary).forEach { c ->
            val spread = maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue)
            assertTrue("$c had a channel spread of $spread", spread <= 0.02f)
        }
    }

    @Test
    fun `success, warning and error are distinct from each other`() {
        listOf(dark, light).forEach { p ->
            assertTrue(p.success != p.warning)
            assertTrue(p.warning != p.error)
            assertTrue(p.success != p.error)
        }
    }

    @Test
    fun `every status colour is legible on its page`() {
        listOf(dark, light).forEach { p ->
            assertTrue(contrast(p.success, p.bgPrimary) >= 3.0)
            assertTrue(contrast(p.warning, p.bgPrimary) >= 3.0)
            assertTrue(contrast(p.error, p.bgPrimary) >= 3.0)
        }
    }

    @Test
    fun `hairlines are visible but not loud`() {
        listOf(dark, light).forEach { p ->
            val c = contrast(p.borderSubtle, p.bgPrimary)
            assertTrue("hairline too faint: $c", c > 1.05)
            assertTrue("hairline too loud: $c", c < 4.5)
        }
    }
}
