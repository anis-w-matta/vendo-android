package com.vendo.app.record

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.vendo.app.common.ErrorSnackbarEffect
import com.vendo.core.designsystem.VendoDarkBlue
import com.vendo.core.designsystem.VendoDarkGray
import com.vendo.core.designsystem.VendoDimens
import com.vendo.core.designsystem.VendoPrimaryBlue
import com.vendo.core.designsystem.VendoWhite
import com.vendo.core.designsystem.components.PillButton
import com.vendo.core.designsystem.components.PillVariant
import com.vendo.core.designsystem.components.RequestCard
import com.vendo.core.designsystem.vendoContentMaxWidth
import com.vendo.core.designsystem.vendoScreenPadding
import kotlinx.coroutines.launch

@Composable
fun RecordScreen(
    onOpenRequest: (Int) -> Unit,
    viewModel: RecordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionPermanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted
        permissionPermanentlyDenied = !granted && run {
            val activity = context as? Activity
            activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.RECORD_AUDIO,
                )
        }
    }

    var showStopConfirm by remember { mutableStateOf(false) }
    BackHandler(enabled = state.isRecording) { showStopConfirm = true }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RecordEvent.OpenRequest -> onOpenRequest(event.requestId)
                RecordEvent.SavedForLater -> snackbarHostState.showSnackbar("Saved - you'll find it in Log Query.")
                RecordEvent.StillProcessing -> snackbarHostState.showSnackbar(
                    "Still working on it. Your recording is safe - check Log Query shortly.",
                )
            }
        }
    }
    ErrorSnackbarEffect(state.error, snackbarHostState)
    ErrorSnackbarEffect(state.hint, snackbarHostState)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .vendoContentMaxWidth()
                .padding(vendoScreenPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                permissionPermanentlyDenied -> PermissionPermanentlyDeniedCard(
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                    onCancel = { permissionPermanentlyDenied = false },
                )
                !hasMicPermission -> PermissionNeededCard(
                    onAllow = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                )
                state.isTakingLong -> TakingLongCard(
                    onKeepWaiting = viewModel::keepWaiting,
                    onStopWaiting = viewModel::stopWaiting,
                )
                state.isSubmitting -> SubmittingContent(stageIndex = state.processingStageIndex)
                state.isTooShort -> TooShortCard(
                    onRecordAgain = viewModel::discardAndReset,
                    onDiscard = viewModel::discardAndReset,
                )
                state.isTranscribing -> TranscribingCard()
                state.hasAudio -> PreviewContent(
                    state = state,
                    onTranscriptEdited = viewModel::onTranscriptEdited,
                    onTogglePreview = viewModel::togglePreviewPlayback,
                    onReviewNow = { viewModel.submit(SubmitMode.REVIEW_NOW) },
                    onSaveForLater = { viewModel.submit(SubmitMode.SAVE_FOR_LATER) },
                )
                else -> IdleOrRecordingContent(state = state, viewModel = viewModel)
            }
        }

        if (showStopConfirm) {
            AlertDialog(
                onDismissRequest = { showStopConfirm = false },
                title = { Text("Discard this recording?") },
                text = { Text("It hasn't been saved yet.") },
                confirmButton = {
                    TextButton(onClick = {
                        showStopConfirm = false
                        viewModel.cancelRecording()
                    }) { Text("Discard") }
                },
                dismissButton = {
                    TextButton(onClick = { showStopConfirm = false }) { Text("Keep recording") }
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun PermissionNeededCard(onAllow: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    RequestCard {
        Text(
            text = "Microphone access required",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "VeNdO needs microphone access to record your order.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PillButton(text = "Allow microphone", onClick = onAllow)
        }
    }
}

@Composable
private fun PermissionPermanentlyDeniedCard(onOpenSettings: () -> Unit, onCancel: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    RequestCard {
        Text(
            text = "Microphone access is turned off for VeNdO",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Turn it back on in Settings to record an order.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(text = "Cancel", variant = PillVariant.DarkGray, onClick = onCancel)
            PillButton(text = "Open Settings", onClick = onOpenSettings)
        }
    }
}

@Composable
private fun TooShortCard(onRecordAgain: () -> Unit, onDiscard: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    RequestCard {
        Text(
            text = "This recording is too short to create an order.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(text = "Discard", variant = PillVariant.DarkGray, onClick = onDiscard)
            PillButton(text = "Record again", onClick = onRecordAgain)
        }
    }
}

@Composable
private fun TranscribingCard() {
    Spacer(Modifier.height(48.dp))
    CircularProgressIndicator()
    Spacer(Modifier.height(16.dp))
    Text(
        text = "We're checking the recording.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun TakingLongCard(onKeepWaiting: () -> Unit, onStopWaiting: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    RequestCard {
        Text(
            text = "This is taking longer than expected.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your recording is safe and still being processed. You can keep waiting, " +
                "or check back later in Log Query.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(text = "I'll check later", variant = PillVariant.DarkGray, onClick = onStopWaiting)
            PillButton(text = "Keep waiting", onClick = onKeepWaiting)
        }
    }
}

@Composable
private fun SubmittingContent(stageIndex: Int) {
    Spacer(Modifier.height(48.dp))
    RequestCard {
        val stages = listOf("Recording saved", "Understanding speech", "Matching products", "Preparing order")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            stages.forEachIndexed { i, label ->
                val step = i + 1
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (symbol, color) = when {
                        step < stageIndex -> "✓" to MaterialTheme.colorScheme.primary
                        step == stageIndex -> "●" to MaterialTheme.colorScheme.primary
                        else -> "○" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(text = symbol, color = color, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (step <= stageIndex) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewContent(
    state: RecordUiState,
    onTranscriptEdited: (String) -> Unit,
    onTogglePreview: () -> Unit,
    onReviewNow: () -> Unit,
    onSaveForLater: () -> Unit,
) {
    Text(
        text = "VeNdO",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(16.dp))
    RequestCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onTogglePreview) {
                Icon(
                    imageVector = if (state.isPlayingPreview) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlayingPreview) "Pause your recording" else "Play your recording",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Your recording",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "What VeNdO heard",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "You can correct this text before submitting - it won't change the recording itself.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = state.transcript,
            onValueChange = onTranscriptEdited,
            modifier = Modifier.fillMaxWidth().height(120.dp),
            shape = RoundedCornerShape(10.dp),
        )
    }
    Spacer(Modifier.height(20.dp))
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(text = "Review Now", variant = PillVariant.PrimaryBlue, onClick = onReviewNow)
        PillButton(text = "Save for Later", variant = PillVariant.DarkBlue, onClick = onSaveForLater)
    }
}

@Composable
private fun IdleOrRecordingContent(state: RecordUiState, viewModel: RecordViewModel) {
    if (!state.isRecording) {
        Text(
            text = "New Order",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tell us what the customer needs.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
    } else {
        Text(
            text = "Recording",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.elapsedLabel,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(14.dp))
        WaveformIndicator()
        Spacer(Modifier.height(14.dp))
    }

    RecordButton(
        isRecording = state.isRecording,
        onClick = {
            if (state.isRecording) viewModel.stopRecording() else viewModel.startRecording()
        },
    )
    Spacer(Modifier.height(16.dp))

    if (state.isRecording) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(text = "Cancel", variant = PillVariant.DarkGray, onClick = viewModel::cancelRecording)
            PillButton(text = "Stop", variant = PillVariant.PrimaryBlue, onClick = viewModel::stopRecording)
        }
        if (state.longRecordingWarning) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "This recording is getting long - VeNdO processes orders up to about 2 minutes.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
            )
        }
    } else {
        Text(
            text = "Tap to record",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WaveformIndicator() {
    val transition = rememberInfiniteTransition(label = "waveform")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            val phase by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 420 + i * 90, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height((28.dp * phase))
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i % 2 == 0) VendoPrimaryBlue else VendoDarkBlue),
            )
        }
    }
}

@Composable
private fun RecordButton(isRecording: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(VendoDimens.RecordButtonSize)
            .clip(CircleShape)
            .background(VendoDarkGray)
            .border(2.dp, VendoDarkGray, CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (isRecording) "Stop recording" else "Start recording"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (isRecording) VendoDimens.RecordButtonDotSizeRecording else VendoDimens.RecordButtonDotSizeIdle)
                .clip(if (isRecording) RoundedCornerShape(4.dp) else CircleShape)
                .background(VendoWhite),
        )
    }
}
