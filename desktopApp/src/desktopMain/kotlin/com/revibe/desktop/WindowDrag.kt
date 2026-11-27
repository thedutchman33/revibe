package com.revibe.desktop

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window

internal enum class WindowResizeEdge { Left, Right, Top, Bottom, BottomRight }

/**
 * Moves [window] with the pointer using screen coordinates.
 *
 * Window-local drag deltas change as the window itself moves, so feeding them back into
 * `setLocation` makes the window chase its own pointer offset and lag or jitter. Anchoring
 * the drag to the global cursor and the window's starting position keeps it 1:1.
 */
internal fun Modifier.moveWindowOnDrag(window: Window, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    return pointerInput(window) {
        var startCursor: Point? = null
        var startWindow = Point()
        detectDragGestures(
            onDragStart = {
                val cursor = MouseInfo.getPointerInfo()?.location
                startCursor = cursor
                if (cursor != null) startWindow = window.location
            },
            onDragEnd = { startCursor = null },
            onDragCancel = { startCursor = null },
            onDrag = { change, _ ->
                change.consume()
                val cursor = MouseInfo.getPointerInfo()?.location
                val origin = startCursor
                if (cursor != null && origin != null) {
                    window.setLocation(
                        startWindow.x + cursor.x - origin.x,
                        startWindow.y + cursor.y - origin.y,
                    )
                }
            },
        )
    }
}

/** Resizes [window] from [edge], anchored to the global cursor and starting bounds. */
internal fun Modifier.resizeWindowOnDrag(
    window: Window,
    edge: WindowResizeEdge,
    minWidth: Int = window.minimumSize.width,
    minHeight: Int = window.minimumSize.height,
    maxWidth: Int = Int.MAX_VALUE,
    maxHeight: Int = Int.MAX_VALUE,
): Modifier = pointerInput(window, edge, minWidth, minHeight, maxWidth, maxHeight) {
    var startCursor: Point? = null
    var startBounds = Rectangle()
    detectDragGestures(
        onDragStart = {
            val cursor = MouseInfo.getPointerInfo()?.location
            startCursor = cursor
            if (cursor != null) startBounds = window.bounds
        },
        onDragEnd = { startCursor = null },
        onDragCancel = { startCursor = null },
        onDrag = { change, _ ->
            change.consume()
            val cursor = MouseInfo.getPointerInfo()?.location
            val origin = startCursor
            if (cursor == null || origin == null) return@detectDragGestures
            val dx = cursor.x - origin.x
            val dy = cursor.y - origin.y
            when (edge) {
                WindowResizeEdge.Right ->
                    window.setSize(
                        (startBounds.width + dx).coerceIn(minWidth, maxWidth),
                        startBounds.height,
                    )
                WindowResizeEdge.Left -> {
                    val width = (startBounds.width - dx).coerceIn(minWidth, maxWidth)
                    window.setBounds(startBounds.x + startBounds.width - width, startBounds.y, width, startBounds.height)
                }
                WindowResizeEdge.Bottom ->
                    window.setSize(
                        startBounds.width,
                        (startBounds.height + dy).coerceIn(minHeight, maxHeight),
                    )
                WindowResizeEdge.Top -> {
                    val height = (startBounds.height - dy).coerceIn(minHeight, maxHeight)
                    window.setBounds(startBounds.x, startBounds.y + startBounds.height - height, startBounds.width, height)
                }
                WindowResizeEdge.BottomRight ->
                    window.setSize(
                        (startBounds.width + dx).coerceIn(minWidth, maxWidth),
                        (startBounds.height + dy).coerceIn(minHeight, maxHeight),
                    )
            }
        },
    )
}
