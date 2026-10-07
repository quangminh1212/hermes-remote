package io.hermes.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.hermes.remote.ui.theme.XLabBrandGradient
import io.hermes.remote.ui.theme.XLabCodeStyle
import io.hermes.remote.ui.theme.XLabThemeColors

/**
 * Shared XLab-branded building blocks.
 *
 * These exist so the chat screen, the settings sheet and the QR screen all draw
 * their headers, chips and labels the same way. The pairing page in
 * `packages/pairing-cli/lib/pairing-page.js` uses the same mark and palette, and
 * is the visual reference for these.
 */

/**
 * The Hermes brand mark: a rounded teal tile with an "H".
 *
 * Matches `.logo` in the pairing page exactly — same gradient, same corner
 * radius, same weight — so the QR page and the app are visibly one product.
 */
@Composable
fun BrandMark(
    size: Dp = 34.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.26f))
            .background(XLabBrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "H",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.5f).sp,
        )
    }
}

/** The mark plus the product name, as a single lockup. */
@Composable
fun BrandLockup(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BrandMark()
        Text(
            text = "Hermes Remote",
            style = MaterialTheme.typography.titleMedium,
            color = XLabThemeColors.textPrimary,
        )
    }
}

/**
 * A small status pill, mirroring `.pill` on the pairing page.
 *
 * [dotColor] is the leading indicator; the pill's tint is derived from it so a
 * green/amber/red state reads at a glance without a second colour argument.
 */
@Composable
fun StatusPill(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(dotColor.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(dotColor),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = dotColor,
            maxLines = 1,
        )
    }
}

/**
 * A key/value line, as used by the `.row` blocks on the pairing page.
 *
 * The value is set in monospace and allowed to shrink, because it is usually a
 * URL, key or agent id where a mid-token wrap is more readable than an
 * ellipsis.
 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = XLabThemeColors.textSecondary,
    divider: Boolean = true,
    /**
     * Replaces the monospace value with arbitrary content.
     *
     * Needed for values that are lists rather than a single token: a wrapped
     * chip row cannot be expressed as one string, and forcing it produced an
     * orphaned word on a second line.
     */
    valueSlot: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = XLabThemeColors.textTertiary,
                modifier = Modifier.width(92.dp),
            )
            Spacer(Modifier.width(12.dp))
            if (valueSlot != null) {
                Box(modifier = Modifier.weight(1f)) { valueSlot() }
            } else {
                Text(
                    text = value,
                    style = XLabCodeStyle,
                    color = valueColor,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (divider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(XLabThemeColors.borderSubtle),
            )
        }
    }
}

/**
 * A section heading with an optional trailing action, used to break the
 * settings sheet into readable groups.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = XLabThemeColors.textPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = XLabThemeColors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        action?.invoke()
    }
}

/** A hairline divider in the system's border colour. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(XLabThemeColors.borderSubtle),
    )
}
