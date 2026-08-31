package com.vendo.app.request

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vendo.core.designsystem.components.StatusBadge
import com.vendo.core.designsystem.components.VendoTone

/** The review screen's "EVIDENCE" card: audio playback and the transcript
 * VeNdO heard, so a reviewer can double-check the draft against the
 * original recording before deciding. */
@Composable
internal fun EvidenceSection(
    state: RequestUiState,
    request: com.vendo.core.network.dto.RequestDetail,
    onTogglePlayback: () -> Unit,
    onSeek: (Int) -> Unit,
) {
    Text(text = "EVIDENCE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(8.dp))
    AudioPlayerRow(state = state, onToggle = onTogglePlayback, onSeek = onSeek)
    Spacer(Modifier.height(12.dp))
    Text(
        text = "What VeNdO heard",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = request.transcript?.takeIf { it.isNotBlank() } ?: "No transcript is available for this recording.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    // config.py's transcript_conf_min (0.5) is the backend's own cutoff for
    // "large parts were a guess" - mirrored here rather than picking an
    // arbitrary UI threshold.
    val conf = request.transcript_conf
    if (conf != null && conf < 0.5) {
        Spacer(Modifier.height(6.dp))
        StatusBadge(text = "Some parts of this recording may be unclear.", tone = VendoTone.Warning)
    }
}

@Composable
private fun AudioPlayerRow(state: RequestUiState, onToggle: () -> Unit, onSeek: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (state.isLoadingAudio) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (state.isPlayingAudio) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlayingAudio) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Slider(
            value = state.playbackPositionMs.toFloat().coerceAtMost(state.playbackDurationMs.toFloat().coerceAtLeast(1f)),
            onValueChange = { onSeek(it.toInt()) },
            valueRange = 0f..state.playbackDurationMs.coerceAtLeast(1).toFloat(),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${formatMs(state.playbackPositionMs)} / ${formatMs(state.playbackDurationMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatMs(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
}
