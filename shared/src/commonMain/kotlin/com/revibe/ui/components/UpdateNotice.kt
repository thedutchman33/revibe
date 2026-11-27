package com.revibe.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.revibe.ui.AppViewModel
import com.revibe.ui.Route
import com.revibe.ui.theme.RevibeColors
import com.revibe.ui.theme.RevibeType
import com.revibe.update.UpdatePhase
import com.revibe.update.UpdateState

/**
 * Non-modal update notice. It sits above the player, never covers transport
 * controls, and never pauses playback.
 */
@Composable
fun UpdateNotice(vm: AppViewModel) {
    val state by vm.updateState.collectAsState()
    if (!state.notify || vm.route == Route.Settings) return
    val update = state.available
    val mandatory = update?.mandatory == true
    val title = when (state.phase) {
        UpdatePhase.AVAILABLE -> if (mandatory) "Required update" else "Update available"
        UpdatePhase.DOWNLOADING -> "Downloading update"
        UpdatePhase.VERIFYING -> "Verifying update"
        UpdatePhase.INSTALLING -> "Installing update"
        UpdatePhase.INSTALLED -> "Update complete"
        UpdatePhase.ERROR -> "Update problem"
        else -> return
    }

    Box(
        Modifier
            .width(376.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(RevibeColors.SurfaceRaised)
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (state.phase) {
                        UpdatePhase.INSTALLED -> Icons.Rounded.CheckCircle
                        UpdatePhase.ERROR -> Icons.Rounded.ErrorOutline
                        else -> Icons.Rounded.SystemUpdate
                    },
                    contentDescription = null,
                    tint = when (state.phase) {
                        UpdatePhase.ERROR -> RevibeColors.Danger
                        UpdatePhase.INSTALLED -> RevibeColors.Accent
                        else -> RevibeColors.TextPrimary
                    },
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary)
                    Text(
                        if (update != null) {
                            "Installed ${state.currentVersion} · Available ${update.version}"
                        } else {
                            "Revibe ${state.currentVersion}"
                        },
                        style = RevibeType.caption,
                        color = RevibeColors.TextSecondary,
                    )
                }
                if (state.phase == UpdatePhase.INSTALLED ||
                    (state.phase == UpdatePhase.AVAILABLE && !mandatory)
                ) {
                    IconAction(
                        Icons.Rounded.Close,
                        contentDescription = "Dismiss update notice",
                        onClick = {
                            if (state.phase == UpdatePhase.INSTALLED) {
                                vm.dismissUpdateNotice()
                            } else {
                                vm.remindUpdateLater()
                            }
                        },
                        size = 26.dp,
                        iconSize = 14.dp,
                    )
                }
            }

            state.message?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    style = RevibeType.caption,
                    color = if (state.phase == UpdatePhase.ERROR) {
                        RevibeColors.Danger
                    } else {
                        RevibeColors.TextSecondary
                    },
                )
            }

            if (state.phase == UpdatePhase.AVAILABLE && update != null && update.releaseNotes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    update.releaseNotes,
                    style = RevibeType.caption,
                    color = RevibeColors.TextTertiary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when (state.phase) {
                UpdatePhase.DOWNLOADING -> {
                    Spacer(Modifier.height(10.dp))
                    UpdateProgress(state.progress)
                }
                UpdatePhase.VERIFYING, UpdatePhase.INSTALLING -> {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = RevibeColors.Accent,
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            if (state.phase == UpdatePhase.INSTALLING) {
                                "Windows Installer continues after Revibe closes."
                            } else {
                                "Checking the download before it is opened."
                            },
                            style = RevibeType.caption,
                            color = RevibeColors.TextTertiary,
                        )
                    }
                }
                else -> Unit
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (state.phase) {
                    UpdatePhase.AVAILABLE -> {
                        RevibeButton("Update now", vm::installUpdate, kind = ButtonKind.Accent)
                        if (state.canRemind) {
                            RevibeButton("Later", vm::remindUpdateLater, kind = ButtonKind.Ghost)
                        }
                        if (state.canSkip) {
                            RevibeButton("Skip version", vm::skipUpdateVersion, kind = ButtonKind.Ghost)
                        }
                    }
                    UpdatePhase.ERROR -> {
                        RevibeButton("Retry", vm::retryUpdate, kind = ButtonKind.Accent)
                        RevibeButton(
                            "Details",
                            { vm.openSettings() },
                            kind = ButtonKind.Ghost,
                        )
                    }
                    UpdatePhase.INSTALLED -> {
                        RevibeButton("Done", vm::dismissUpdateNotice, kind = ButtonKind.Accent)
                    }
                    else -> {
                        Text(
                            "You can keep listening.",
                            style = RevibeType.caption,
                            color = RevibeColors.TextTertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun UpdateProgress(progress: Float?) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(RevibeColors.SurfaceActive),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress?.coerceIn(0f, 1f) ?: 0f)
                .height(4.dp)
                .background(RevibeColors.Accent),
        )
    }
}
