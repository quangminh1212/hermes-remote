package io.hermes.remote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.hermes.remote.R
import io.hermes.remote.model.GatewayInfo
import io.hermes.remote.ui.theme.XLabThemeColors

/**
 * A compact read-out of the Hermes gateway running on the PC.
 *
 * This is the visible half of "keep the app in sync with the PC": the user
 * opens the chat and immediately sees whether the machine they are talking to
 * is healthy, what it can do, and what was discussed before. It deliberately
 * shows problems (a full disk, a dead platform) rather than a green dot,
 * because a silent assistant that is half-broken is worse than a noisy one.
 *
 * The card is forgiving of missing data: every line is gated on the field
 * actually being present, so an older gateway that omits a field simply shows
 * less rather than a blank placeholder.
 *
 * @param info the latest snapshot, or null before the first fetch
 * @param loading whether a refresh is in flight
 * @param onRefresh request a fresh snapshot
 */
@Composable
fun GatewayStatusCard(
    info: GatewayInfo?,
    loading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val title = if (info != null && info.version.isNotBlank()) {
                stringResource(R.string.gw_version, info.version)
            } else {
                stringResource(R.string.gw_section_title)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = XLabThemeColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (loading) {
                Text(
                    text = "…",
                    color = XLabThemeColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (info == null) {
            if (loading) {
                Text(
                    text = stringResource(R.string.gw_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = XLabThemeColors.textTertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            return
        }

        // Health line: green when ok, amber when the PC flagged a problem.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dot = if (info.isDegraded) XLabThemeColors.warning else XLabThemeColors.primary
            Text(
                text = "● ",
                color = dot,
                style = MaterialTheme.typography.bodySmall,
            )
            val statusText = if (info.isDegraded) stringResource(R.string.gw_status_degraded) else stringResource(R.string.gw_status_ok)
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = XLabThemeColors.textSecondary,
            )
        }

        // Disk: only when the gateway reported it, and only worth a line when
        // it is actually filling up.
        info.diskUsedPercent?.let { pct ->
            if (pct >= 80.0) {
                Text(
                    text = stringResource(R.string.gw_disk_low, pct.toInt(), info.freeDiskGb ?: 0.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pct >= 90.0) XLabThemeColors.warning else XLabThemeColors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // Broken platforms: WhatsApp/Slack/… that failed to connect on the PC.
        // This is the most actionable line — the user can fix it on the PC.
        if (info.brokenPlatforms.isNotEmpty()) {
            Text(
                text = stringResource(R.string.gw_platform_down, info.brokenPlatforms.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = XLabThemeColors.warning,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // Capabilities and history, as quick counts.
        val counts = listOfNotNull(
            if (info.toolsets.isNotEmpty()) {
                stringResource(R.string.gw_tools, info.toolsets.size)
            } else {
                null
            },
            if (info.sessions.isNotEmpty()) {
                stringResource(R.string.gw_sessions, info.sessions.size)
            } else {
                null
            },
        )
        if (counts.isNotEmpty()) {
            Text(
                text = counts.joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = XLabThemeColors.textTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
