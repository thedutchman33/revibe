package com.revibe.desktop

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
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.revibe.createDesktopGraph
import com.revibe.platform.DesktopMediaKeys
import com.revibe.ui.AppViewModel
import com.revibe.ui.RevibeApp
import com.revibe.ui.components.IconAction
import com.revibe.ui.components.LocalArtworkLoader
import com.revibe.ui.components.TopBar
import com.revibe.ui.theme.LocalAccent
import com.revibe.ui.theme.RevibeColors
import com.revibe.ui.theme.RevibeTheme
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.Rectangle
import java.awt.Window
import javax.imageio.ImageIO

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
        // The update service runs on a background dispatcher; marshal the window
        // teardown to Swing's event queue after the installer handoff starts.
        graph.updates.onShutdownRequested = {
            javax.swing.SwingUtilities.invokeLater { closeApp() }
        }
        var maximized by remember { mutableStateOf(false) }
        var restoreBounds by remember { mutableStateOf<Rectangle?>(null) }

        Window(
            onCloseRequest = closeApp,
            title = "Revibe",
            state = windowState,
            undecorated = true,
            onPreviewKeyEvent = keyHandler@{ event ->
                if (event.type != KeyEventType.KeyDown) return@keyHandler false
                when {
                    event.isCtrlPressed && event.key == Key.F -> { vm.openSearch(); true }
                    event.isCtrlPressed && event.key == Key.Spacebar -> { vm.togglePlayPause(); true }
                    event.isCtrlPressed && event.key == Key.DirectionRight -> { vm.nextTrack(); true }
                    event.isCtrlPressed && event.key == Key.DirectionLeft -> { vm.previousTrack(); true }
                    event.isCtrlPressed && event.key == Key.DirectionUp -> {
                        vm.setVolume((vm.playerState.value.volume + 0.05f).coerceAtMost(1f)); true
                    }
                    event.isCtrlPressed && event.key == Key.DirectionDown -> {
                        vm.setVolume((vm.playerState.value.volume - 0.05f).coerceAtLeast(0f)); true
                    }
                    event.isCtrlPressed && event.key == Key.L -> {
                        vm.playerState.value.current?.let(vm::toggleLike); true
                    }
                    event.key == Key.Escape && vm.nowPlayingExpanded -> {
                        vm.nowPlayingExpanded = false; true
                    }
                    else -> false
                }
            },
        ) {
            // AWT window bounds are logical units here; the compositor applies DPI scale.
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(1120, 720)
                window.background = java.awt.Color(8, 9, 10)
                AppIcon.load()?.let { window.iconImage = it }
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
                                .moveWindowOnDrag(window, enabled = !maximized),
                        ) {
                            TopBar(vm) {
                                WindowControls(
                                    maximized = maximized,
                                    onMinimize = { windowState.isMinimized = true },
                                    onToggleMaximize = {
                                        if (maximized) {
                                            restoreBounds?.let(window::setBounds)
                                            maximized = false
                                        } else {
                                            restoreBounds = window.bounds
                                            // Undecorated Maximized placement covers the taskbar on
                                            // Windows; the work area stops above it instead.
                                            window.setBounds(
                                                GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds,
                                            )
                                            maximized = true
                                        }
                                    },
                                    onClose = closeApp,
                                )
                            }
                        }
                    },
                )
                if (!maximized) WindowResizeHandles(window)
            }
        }

        // Spotify-style floating miniplayer: a second, always-on-top window while the
        // main window stays open behind it.
        if (vm.miniplayer) {
            val miniWidth = 400.dp
            val miniHeight = 336.dp
            val work = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
            // AWT work-area units map 1:1 to dp on this host, so the card can be
            // placed above the taskbar without a post-show setLocation race.
            val miniState = rememberWindowState(
                width = miniWidth,
                height = miniHeight,
                position = WindowPosition(
                    (work.x + work.width - miniWidth.value.toInt() - 24).dp,
                    (work.y + work.height - miniHeight.value.toInt() - 24).dp,
                ),
            )
            Window(
                onCloseRequest = vm::exitMiniplayer,
                title = "Revibe Miniplayer",
                state = miniState,
                undecorated = true,
                transparent = true,
                resizable = false,
                alwaysOnTop = true,
            ) {
                LaunchedEffect(Unit) {
                    window.minimumSize = Dimension(356, 300)
                    AppIcon.load()?.let { window.iconImage = it }
                }
                // The compact window is a separate composition, so it must provide the
                // artwork loader and theme itself or Artwork throws on first frame.
                CompositionLocalProvider(
                    LocalArtworkLoader provides vm.artworkLoader,
                    LocalAccent provides vm.accent,
                ) {
                    RevibeTheme { MiniPlayer(vm, window) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.WindowControls(
    maximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    IconAction(
        Icons.Rounded.Remove,
        contentDescription = "Minimize",
        onClick = onMinimize,
        size = 38.dp,
        iconSize = 16.dp,
        tint = RevibeColors.TextSecondary,
    )
    IconAction(
        if (maximized) Icons.Rounded.FilterNone else Icons.Rounded.CropSquare,
        contentDescription = if (maximized) "Restore" else "Maximize",
        onClick = onToggleMaximize,
        size = 38.dp,
        iconSize = 16.dp,
        tint = RevibeColors.TextSecondary,
    )
    IconAction(
        Icons.Rounded.Close,
        contentDescription = "Close",
        onClick = onClose,
        size = 38.dp,
        iconSize = 16.dp,
        tint = RevibeColors.TextSecondary,
        hoverTint = RevibeColors.Danger,
    )
}

private object AppIcon {
    fun load(): Image? = runCatching {
        AppIcon::class.java.getResourceAsStream("/revibe.png")?.use { ImageIO.read(it) }
    }.getOrNull()
}

@Composable
private fun BoxScope.WindowResizeHandles(window: java.awt.Window) {
    val minWidth = window.minimumSize.width
    val minHeight = window.minimumSize.height
    val edge = 5.dp

    Box(
        Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(edge)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .resizeWindowOnDrag(window, WindowResizeEdge.Right, minWidth, minHeight),
    )
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .width(edge)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.W_RESIZE_CURSOR)))
            .resizeWindowOnDrag(window, WindowResizeEdge.Left, minWidth, minHeight),
    )
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(edge)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.S_RESIZE_CURSOR)))
            .resizeWindowOnDrag(window, WindowResizeEdge.Bottom, minWidth, minHeight),
    )
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(edge)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
            .resizeWindowOnDrag(window, WindowResizeEdge.Top, minWidth, minHeight),
    )
}
