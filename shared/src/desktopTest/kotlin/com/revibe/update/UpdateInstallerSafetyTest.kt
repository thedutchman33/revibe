package com.revibe.update

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import java.util.concurrent.TimeUnit

class UpdateInstallerSafetyTest {
    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("revibe-installer-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `unpackaged build never launches an installer`() = runBlocking {
        val installer = File(tempDir, "Revibe-0.10.0-Setup.exe").apply { writeText("fake exe") }
        val handoff = WindowsInstaller(tempDir, packagedAppPath = null)
            .launch(installer.absolutePath, "0.10.0")
        assertFalse(handoff.accepted)
        assertTrue(installer.exists())
    }

    @Test
    fun `unsigned installer is not accepted as trusted`() = runBlocking {
        val installer = File(tempDir, "Revibe-0.10.0-Setup.exe").apply { writeText("not signed") }
        val check = WindowsPublisherVerifier().verify(installer.absolutePath, "CN=Revibe")
        assertNotEquals(PublisherStatus.VALID, check.status)
    }

    @Test
    fun `generated updater script has valid PowerShell syntax`() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) return
        val script = updaterScript(
            appPid = 1234L,
            installerPath = "C:\\Users\\Test User\\AppData\\Roaming\\Revibe\\updates\\downloads\\Revibe-0.10.0-Setup.exe",
            appPath = "C:\\Program Files\\Revibe\\Revibe.exe",
            resultPath = "C:\\Users\\Test User\\AppData\\Roaming\\Revibe\\updates\\install-result.json",
            targetVersion = "0.10.0",
        )
        assertTrue(script.contains("/VERYSILENT"), "Inno silent switches are missing")
        assertTrue(script.contains("/CLOSEAPPLICATIONS"), "Inno close-applications switch is missing")
        val scriptFile = File(tempDir, "parse-updater.ps1").apply { writeText(script) }
        val command = """
            ${'$'}tokens = ${'$'}null
            ${'$'}errors = ${'$'}null
            [System.Management.Automation.Language.Parser]::ParseFile('${scriptFile.absolutePath.replace("'", "''")}', [ref]${'$'}tokens, [ref]${'$'}errors) | Out-Null
            if (${'$'}errors.Count -gt 0) {
                ${'$'}errors | ForEach-Object { Write-Output ${'$'}_.Message }
                exit 1
            }
            exit 0
        """.trimIndent()
        val process = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command,
        ).redirectErrorStream(true).start()
        val finished = process.waitFor(30, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!finished) process.destroyForcibly()
        assertTrue(finished, "PowerShell parser timed out")
        assertEquals(0, process.exitValue(), output)
    }

    @Test
    fun `installer URL accepts EXE and MSI but rejects other files`() {
        assertEquals("exe", windowsInstallerExtension("https://example.test/Revibe-1.0.0-Setup.exe?x=1"))
        assertEquals("msi", windowsInstallerExtension("https://example.test/Revibe-1.0.0.msi"))
        assertEquals(null, windowsInstallerExtension("https://example.test/Revibe.zip"))
    }

    @Test
    fun `missing installer cannot be verified`() = runBlocking {
        val check = WindowsPublisherVerifier().verify(
            File(tempDir, "missing.msi").absolutePath,
            "CN=Revibe",
        )
        assertEquals(PublisherStatus.UNAVAILABLE, check.status)
    }
}
