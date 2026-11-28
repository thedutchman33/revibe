package com.revibe.platform

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Windows' own "Open" dialog (the shell Common Item Dialog) instead of Swing's
 * [javax.swing.JFileChooser], which paints a dated non-native window on Windows.
 * WinForms' OpenFileDialog is the shell dialog, so the sidebar, breadcrumb, search
 * box, filter dropdown and keyboard handling all match the rest of the OS.
 *
 * The dialog is shown by a short-lived PowerShell process, the same pattern the
 * updater uses for its handoff. Callers must run this off the UI thread.
 */
internal object WindowsFileDialog {
    fun isAvailable(): Boolean =
        System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)

    /**
     * Returns the chosen file, or `null` when the user cancelled. Throws when the
     * dialog could not be shown at all, so callers can fall back to Swing.
     */
    fun open(title: String, filterLabel: String, extensions: List<String>, initialDirectory: File?): File? {
        check(isAvailable()) { "The native file dialog is Windows-only." }
        val workDirectory = Files.createTempDirectory("revibe-picker").toFile()
        try {
            val scriptFile = File(workDirectory, "pick-file.ps1")
            val outputFile = File(workDirectory, "picked-file.txt")
            val filter = buildFilter(filterLabel, extensions)
            scriptFile.writeBytes(
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                    script(title, filter, initialDirectory?.absolutePath.orEmpty(), outputFile.absolutePath)
                        .toByteArray(Charsets.UTF_8),
            )
            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-STA",
                "-ExecutionPolicy",
                "Bypass",
                "-WindowStyle",
                "Hidden",
                "-File",
                scriptFile.absolutePath,
            )
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(DIALOG_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw IllegalStateException("The Windows file dialog timed out.")
            }
            check(process.exitValue() == 0) { "The Windows file dialog exited with ${process.exitValue()}." }
            if (!outputFile.isFile) return null
            return outputFile.readText(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }?.let(::File)
        } finally {
            workDirectory.deleteRecursively()
        }
    }

    private fun buildFilter(filterLabel: String, extensions: List<String>): String {
        val patterns = extensions.joinToString(";") { "*.$it" }
        return "$filterLabel|$patterns|All files (*.*)|*.*"
    }

    private fun script(title: String, filter: String, initialDirectory: String, outputPath: String): String = """
        ${'$'}ErrorActionPreference = 'Stop'
        Add-Type -AssemblyName System.Windows.Forms
        ${'$'}dialog = New-Object System.Windows.Forms.OpenFileDialog
        ${'$'}dialog.Title = '${quote(title)}'
        ${'$'}dialog.Filter = '${quote(filter)}'
        ${'$'}dialog.CheckFileExists = ${'$'}true
        ${'$'}dialog.Multiselect = ${'$'}false
        ${'$'}dialog.RestoreDirectory = ${'$'}true
        ${'$'}initial = '${quote(initialDirectory)}'
        if (${'$'}initial -and (Test-Path -LiteralPath ${'$'}initial)) {
            ${'$'}dialog.InitialDirectory = ${'$'}initial
        }
        if (${'$'}dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
            [System.IO.File]::WriteAllText(
                '${quote(outputPath)}',
                ${'$'}dialog.FileName,
                (New-Object System.Text.UTF8Encoding(${'$'}false))
            )
        }
    """.trimIndent()

    private fun quote(value: String): String = value.replace("'", "''")

    private const val DIALOG_TIMEOUT_SECONDS = 900L
}
