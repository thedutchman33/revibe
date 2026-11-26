package com.revibe.desktop

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.revibe.createDesktopGraph
import com.revibe.platform.DesktopMediaKeys
import com.revibe.ui.AppViewModel
import com.revibe.ui.RevibeApp
import com.revibe.ui.components.IconAction
import com.revibe.ui.components.TopBar
import com.revibe.ui.theme.RevibeColors
import java.awt.Cursor
import java.awt.Dimension

fun main() {
    val graph = createDesktopGraph()
    val vm = AppViewModel(graph)
    val mediaKeys = DesktopMediaKeys(
        onPlayPause = vm::togglePlayPause,
        onNext = vm::nextTrack,
        onPrevious = vm::previousTrack,
    )

    application {
        val windowState = rememberWindowState(
            width = 1440.dp,
            height = 900.dp,
            position = WindowPosition.Aligned(Alignment.Center),
        )
        val closeApp: () -> Unit = {
            mediaKeys.uninstall()
            vm.shutdown()
            exitApplication()
        }

        Window(
            onCloseRequest = closeApp,
            title = "Revibe",
            state = windowState,
            undecorated = true,
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(1120, 720)
                window.background = java.awt.Color(8, 9, 10)
                mediaKeys.install()
            }
            DisposableEffect(Unit) {
                onDispose { mediaKeys.uninstall() }
            }

            Box(Modifier.fillMaxSize()) {
                RevibeApp(
                    vm = vm,
                    topBar = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .pointerInput(windowState.placement) {
                                    if (windowState.placement == WindowPlacement.Floating) {
                                        detectDragGestures { change, drag ->
                                            change.consume()
                                            window.setLocation(
                                                window.x + drag.x.toInt(),
                                                window.y + drag.y.toInt(),
                                            )
                                        }
                                    }
                                },
                        ) {
                            TopBar(vm) {
                                WindowControls(windowState, closeApp)
                            }
                        }
                    },
                )
                WindowResizeHandles(window)
            }
        }
    }
}

@Composable
private fun RowScope.WindowControls(windowState: WindowState, onClose: () -> Unit) {
    IconAction(
        Icons.Rounded.Remove,
        contentDescription = "Minimize",
        onClick = { windowState.isMinimized = true },
        size = 38.dp,
        iconSize = 15.dp,
        tint = RevibeColors.TextSecondary,
    )
    IconAction(
        Icons.Rounded.CropSquare,
        contentDescription = "Maximize",
        onClick = {
            windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                WindowPlacement.Floating
            } else {
                WindowPlacement.Maximized
            }
        },
        size = 38.dp,
        iconSize = 13.dp,
        tint = RevibeColors.TextSecondary,
    )
    IconAction(
        Icons.Rounded.Close,
        contentDescription = "Close",
        onClick = onClose,
        size = 38.dp,
        iconSize = 15.dp,
        tint = RevibeColors.TextSecondary,
        hoverTint = RevibeColors.Danger,
    )
}

@Composable
private fun BoxScope.WindowResizeHandles(window: java.awt.Window) {
    val minWidth = 1120
    val minHeight = 720
    val edge = 5.dp

    Box(
        Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(edge)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, drag ->
                    change.consume()
                    window.setSize((window.width + drag.toInt()).coerceAtLeast(minWidth), window.height)
                }
            },
    )
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .width(edge)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.W_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, drag ->
                    change.consume()
                    val oldWidth = window.width
                    val newWidth = (oldWidth - drag.toInt()).coerceAtLeast(minWidth)
                    window.setSize(newWidth, window.height)
                    window.setLocation(window.x + (oldWidth - newWidth), window.y)
                }
            },
    )
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(edge)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.S_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, drag ->
                    change.consume()
                    window.setSize(window.width, (window.height + drag.toInt()).coerceAtLeast(minHeight))
                }
            },
    )
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(edge)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, drag ->
                    change.consume()
                    val oldHeight = window.height
                    val newHeight = (oldHeight - drag.toInt()).coerceAtLeast(minHeight)
                    window.setSize(window.width, newHeight)
                    window.setLocation(window.x, window.y + (oldHeight - newHeight))
                }
            },
    )
}
