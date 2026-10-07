package io.hermes.remote.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The handful of icons this app needs, defined inline.
 *
 * Why not `material-icons-extended`: it is a multi-megabyte dependency for
 * fewer than ten glyphs, and the project is on Compose BOM 2023.08 whose
 * `material-icons-core` ships only a small curated set — Mic, Stop and VolumeUp
 * are all in the *extended* artefact. Adding it would grow the APK for no
 * benefit when the paths are a dozen lines each.
 *
 * All paths are 24x24, matching the Material grid, so they align optically with
 * the icons that *do* come from core (Settings, Send, Close).
 */
object HermesIcons {

    /** Microphone: a body capsule, a cradle arc and a stem. */
    val Mic: ImageVector by lazy {
        materialIcon("Mic") {
            path(fill = SolidColor(Color.Black)) {
                // Capsule.
                moveTo(12f, 14f)
                curveTo(14.21f, 14f, 16f, 12.21f, 16f, 10f)
                verticalLineTo(5f)
                curveTo(16f, 2.79f, 14.21f, 1f, 12f, 1f)
                curveTo(9.79f, 1f, 8f, 2.79f, 8f, 5f)
                verticalLineTo(10f)
                curveTo(8f, 12.21f, 9.79f, 14f, 12f, 14f)
                close()
                // Cradle.
                moveTo(19f, 10f)
                verticalLineTo(12f)
                curveTo(19f, 15.53f, 16.39f, 18.44f, 12.97f, 18.93f)
                verticalLineTo(21f)
                horizontalLineTo(15.5f)
                verticalLineTo(23f)
                horizontalLineTo(8.5f)
                verticalLineTo(21f)
                horizontalLineTo(11.03f)
                verticalLineTo(18.93f)
                curveTo(7.61f, 18.44f, 5f, 15.53f, 5f, 12f)
                verticalLineTo(10f)
                horizontalLineTo(7f)
                verticalLineTo(12f)
                curveTo(7f, 14.76f, 9.24f, 17f, 12f, 17f)
                curveTo(14.76f, 17f, 17f, 14.76f, 17f, 12f)
                verticalLineTo(10f)
                close()
            }
        }
    }

    /** Stop: a filled rounded square, used for "cancel this capture". */
    val Stop: ImageVector by lazy {
        materialIcon("Stop") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 6f)
                horizontalLineTo(18f)
                verticalLineTo(18f)
                horizontalLineTo(6f)
                close()
            }
        }
    }

    /** Speaker with waves: "read this aloud" / "stop reading". */
    val VolumeUp: ImageVector by lazy {
        materialIcon("VolumeUp") {
            path(fill = SolidColor(Color.Black)) {
                // Cone.
                moveTo(3f, 9f)
                verticalLineTo(15f)
                horizontalLineTo(7f)
                lineTo(12f, 20f)
                verticalLineTo(4f)
                lineTo(7f, 9f)
                close()
                // Waves.
                moveTo(16.5f, 12f)
                curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f)
                verticalLineTo(16.02f)
                curveTo(15.48f, 15.29f, 16.5f, 13.77f, 16.5f, 12f)
                close()
                moveTo(14f, 3.23f)
                verticalLineTo(5.29f)
                curveTo(16.89f, 6.15f, 19f, 8.83f, 19f, 12f)
                curveTo(19f, 15.17f, 16.89f, 17.85f, 14f, 18.71f)
                verticalLineTo(20.77f)
                curveTo(18f, 19.86f, 21f, 16.28f, 21f, 12f)
                curveTo(21f, 7.72f, 18f, 4.14f, 14f, 3.23f)
                close()
            }
        }
    }

    /**
     * Build an [ImageVector] with the shared 24dp viewport.
     *
     * Tinting is left to the call site (`Icon(tint = …)`) because a vector's
     * own fill is only a placeholder for `Icon`'s tint.
     */
    private fun materialIcon(
        name: String,
        block: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()
}

/** Convenience wrapper so call sites do not repeat the size boilerplate. */
@Composable
fun SmallIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 20.dp,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}
