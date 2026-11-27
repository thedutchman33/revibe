package com.revibe.ui.components

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

actual fun panelResizeCursor(): PointerIcon = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
