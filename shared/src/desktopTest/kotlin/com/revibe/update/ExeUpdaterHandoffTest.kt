package com.revibe.update

import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the exact production updater helper against a real Inno Setup EXE.
 *
 * CI installs the previous release into the directory supplied here, then runs
 * this test with the newly built installer. Without those variables the test is
 * a no-op, so the normal unit suite does not need an installer artifact.
 */
class ExeUpdaterHandoffTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `real Inno installer handoff upgrades the installed app and reports success`() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) return
        val installer = System.getenv("REVIBE_TEST_UPDATER_INSTALLER")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: return
        val appDirectory = System.getenv("REVIBE_TEST_UPDATER_APP_DIR")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: return
        val targetVersion = System.getenv("REVIBE_TEST_UPDATER_VERSION")
            ?.takeIf { it.isNotBlank() }
            ?: return
        val app = File(appDirectory, "Revibe.exe")
        assertTrue(installer.isFile, "Installer is missing: ${installer.absolutePath}")
        assertTrue(app.isFile, "The previous release is not installed at ${app.absolutePath}")

        val resultFile = File.createTempFile("revibe-updater-result", ".json").apply { delete() }
        val scriptFile = File.createTempFile("revibe-updater", ".ps1")
        try {
            val script = updaterScript(
                appPid = 999_999L,
                installerPath = installer.absolutePath,
                appPath = app.absolutePath,
                resultPath = resultFile.absolutePath,
                targetVersion = targetVersion,
            )
            scriptFile.writeBytes(
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                    script.toByteArray(Charsets.UTF_8),
            )

            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                scriptFile.absolutePath,
            ).redirectErrorStream(true).start()
            val finished = process.waitFor(300, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (!finished) process.destroyForcibly()
            assertTrue(finished, "Installer handoff timed out")
            assertEquals(0, process.exitValue(), output)

            assertTrue(resultFile.isFile, "The updater did not write an install result")
            val result = json.decodeFromString(
                InstallResult.serializer(),
                resultFile.readText().removePrefix("\uFEFF"),
            )
            assertEquals(0, result.exitCode, result.message)
            assertEquals(targetVersion, result.targetVersion)
            assertTrue(app.isFile, "Revibe.exe is missing after the upgrade")
        } finally {
            resultFile.delete()
            scriptFile.delete()
            stopRelaunchedApp(app.absolutePath)
        }
    }

    private fun stopRelaunchedApp(appPath: String) {
        if (!appPath.contains("Revibe", ignoreCase = true)) return
        val escaped = appPath.replace("'", "''")
        runCatching {
            ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "Get-CimInstance Win32_Process | Where-Object { \$_.ExecutablePath -eq '$escaped' } | " +
                    "ForEach-Object { Stop-Process -Id \$_.ProcessId -Force -ErrorAction SilentlyContinue }",
            ).redirectErrorStream(true).start().waitFor(30, TimeUnit.SECONDS)
        }
    }
}
