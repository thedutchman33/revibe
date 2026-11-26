package com.revibe.platform

import com.revibe.FolderPicker
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyAdapter
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.JFileChooser
import javax.swing.UIManager

class SwingFolderPicker : FolderPicker {
    override suspend fun pickFolder(): String? = withContext(Dispatchers.Swing) {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose a music folder"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isMultiSelectionEnabled = false
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.absolutePath
        } else {
            null
        }
    }
}

/**
 * Windows media keys through a global hook. This covers play/pause, next and
 * previous even when Revibe is in the background. System Media Transport
 * Controls (the Windows media overlay) need a WinRT bridge and are tracked as a
 * follow-up in docs/PLAYBACK_ENGINE.md.
 */
class DesktopMediaKeys(
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
) {
    private var installed = false

    fun install() {
        if (installed) return
        runCatching {
            Logger.getLogger("org.jnativehook").level = Level.WARNING
            GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeKeyListener(
                object : NativeKeyAdapter() {
                    override fun nativeKeyPressed(event: NativeKeyEvent) {
                        // Windows virtual-key codes. JNativeHook's VC_* names differ between releases,
                        // so the raw codes are the stable contract here.
                        when (event.rawCode) {
                            0xB3, 179 -> onPlayPause()  // VK_MEDIA_PLAY_PAUSE
                            0xB0, 176 -> onNext()       // VK_MEDIA_NEXT_TRACK
                            0xB1, 177 -> onPrevious()   // VK_MEDIA_PREV_TRACK
                        }
                    }
                },
            )
            installed = true
        }
    }

    fun uninstall() {
        if (!installed) return
        runCatching { GlobalScreen.unregisterNativeHook() }
        installed = false
    }
}
