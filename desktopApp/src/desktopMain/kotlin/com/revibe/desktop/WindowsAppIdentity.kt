package com.revibe.desktop

import com.sun.jna.WString
import com.sun.jna.platform.win32.Shell32
import java.awt.Image
import java.awt.Taskbar

/**
 * Windows groups taskbar buttons by AppUserModelID. Without an explicit ID the
 * JVM gets a generic identity, so the Revibe icon from the shortcut and EXE is
 * not used for the running window. The installer assigns the same ID to the
 * Start-menu and desktop shortcuts.
 */
internal object WindowsAppIdentity {
    const val APP_ID = "Revibe.Desktop"

    fun install() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return
        runCatching { Shell32.INSTANCE.SetCurrentProcessExplicitAppUserModelID(WString(APP_ID)) }
    }

    fun applyIcon(image: Image?) {
        image ?: return
        runCatching {
            val taskbar = Taskbar.getTaskbar()
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) taskbar.iconImage = image
        }
    }
}
