package com.revibe.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.revibe.model.LayoutSpec
import com.revibe.ui.theme.RevibeColors

/**
 * The 8 dp gutter that resizes an adjacent Spotify-style container. The hit area
 * is always live; on hover the centred grip from the reference recording fades in.
 */
@Composable
fun VerticalResizeHandle(
    onDrag: (deltaDp: Float) -> Unit,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = hovered || dragging

    Box(
        modifier
            .fillMaxHeight()
            .width(LayoutSpec.GUTTER.dp)
            .hoverable(interaction)
            .pointerHoverIcon(panelResizeCursor())
            .pointerInput(density) {
                detectDragGestures(
                    onDragStart = { dragging = true; onDragStart() },
                    onDragEnd = { dragging = false; onDragEnd() },
                    onDragCancel = { dragging = false; onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(with(density) { dragAmount.x.toDp().value })
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = active, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .width(5.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(RevibeColors.TextPrimary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) {
                        Box(
                            Modifier
                                .size(2.dp)
                                .clip(CircleShape)
                                .background(if (dragging) RevibeColors.Accent else RevibeColors.TextPrimary.copy(alpha = 0.75f)),
                        )
                    }
                }
            }
        }
    }
}
