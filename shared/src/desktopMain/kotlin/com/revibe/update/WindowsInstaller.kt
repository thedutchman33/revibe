package com.revibe.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Never replaces running files. The app exits, a separate PowerShell process
 * waits for this PID, runs the verified Inno Setup EXE or MSI, writes the exit
 * code, then relaunches Revibe. The next launch reads that result.
 */
class WindowsInstaller(
    private val updatesDirectory: File,
    private val packagedAppPath: String? = detectPackagedAppPath(),
) : UpdateInstaller {
    override suspend fun launch(installerPath: String, targetVersion: String): InstallHandoff =
        withContext(Dispatchers.IO) {
            if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
                return@withContext InstallHandoff(false, "Automatic installation is Windows-only.")
            }
            val installer = File(installerPath)
            if (!installer.isFile) {
                return@withContext InstallHandoff(false, "The downloaded installer is missing.")
            }
            if (installer.extension.lowercase() !in setOf("exe", "msi")) {
                return@withContext InstallHandoff(
                    false,
                    "Only a Windows EXE or MSI can be installed automatically.",
                )
            }
            val appPath = packagedAppPath?.takeIf { File(it).isFile }
                ?: return@withContext InstallHandoff(
                    false,
                    "Automatic installation is available in the packaged Windows build only.",
                )
            updatesDirectory.mkdirs()
            val scriptFile = File(updatesDirectory, "run-update.ps1")
            val resultFile = File(updatesDirectory, "install-result.json")
            val logFile = File(updatesDirectory, "updater.log")
            resultFile.delete()

            val currentPid = ProcessHandle.current().pid()
            val script = updaterScript(
                appPid = currentPid,
                installerPath = installer.absolutePath,
                appPath = appPath,
                resultPath = resultFile.absolutePath,
                targetVersion = targetVersion,
            )
            // Windows PowerShell 5 treats BOM-less files as ANSI; use UTF-8 BOM for Unicode paths.
            scriptFile.writeBytes(
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                    script.toByteArray(Charsets.UTF_8),
            )

            val process = runCatching {
                ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-WindowStyle",
                    "Hidden",
                    "-File",
                    scriptFile.absolutePath,
                )
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                    .start()
            }.getOrElse {
                return@withContext InstallHandoff(
                    false,
                    "The updater process could not be started.",
                )
            }
            // Confirm the helper is still running before the app is allowed to close.
            val exited = runCatching { process.waitFor(500, TimeUnit.MILLISECONDS) }.getOrDefault(false)
            if (exited) {
                return@withContext InstallHandoff(false, "The updater process exited before starting.")
            }
            InstallHandoff(true)
        }

    override suspend fun discard(installerPath: String) = withContext(Dispatchers.IO) {
        File(installerPath).delete()
        File("$installerPath.part").delete()
        Unit
    }

    private companion object {
        fun detectPackagedAppPath(): String? {
            val jpackagePath = System.getProperty("jpackage.app-path")
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.isFile }
            if (jpackagePath != null) return jpackagePath.absolutePath

            val command = ProcessHandle.current().info().command().orElse(null) ?: return null
            val executable = File(command)
            if (!executable.isFile) return null
            if (executable.name.equals("java.exe", true) ||
                executable.name.equals("javaw.exe", true)
            ) {
                return null
            }
            return executable.absolutePath.takeIf { executable.extension.equals("exe", true) }
        }
    }
}

internal fun updaterScript(
    appPid: Long,
    installerPath: String,
    appPath: String,
    resultPath: String,
    targetVersion: String,
): String {
    fun quote(value: String) = value.replace("'", "''")
    return """
            ${'$'}ErrorActionPreference = 'Stop'
            ${'$'}exitCode = -1
            ${'$'}message = ''
            ${'$'}installer = '${quote(installerPath)}'
            ${'$'}app = '${quote(appPath)}'
            ${'$'}result = '${quote(resultPath)}'
            try {
                ${'$'}deadline = (Get-Date).AddSeconds(180)
                while ((Get-Process -Id $appPid -ErrorAction SilentlyContinue) -and
                    ((Get-Date) -lt ${'$'}deadline)) {
                    Start-Sleep -Milliseconds 250
                }
                ${'$'}extension = [IO.Path]::GetExtension(${'$'}installer).ToLowerInvariant()
                if (${'$'}extension -eq '.exe') {
                    ${'$'}process = Start-Process -FilePath ${'$'}installer `
                        -ArgumentList @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART', '/SP-', '/CLOSEAPPLICATIONS') `
                        -Wait -PassThru
                } else {
                    ${'$'}msiexec = Join-Path ${'$'}env:SystemRoot 'System32\msiexec.exe'
                    ${'$'}process = Start-Process -FilePath ${'$'}msiexec `
                        -ArgumentList @('/i', ('"' + ${'$'}installer + '"'), '/passive', '/norestart') `
                        -Wait -PassThru
                }
                ${'$'}exitCode = [int]${'$'}process.ExitCode
            } catch {
                ${'$'}exitCode = -1
                ${'$'}message = ${'$'}_.Exception.Message
            }
            ${'$'}payload = [ordered]@{
                exitCode = ${'$'}exitCode
                targetVersion = '${quote(targetVersion)}'
                installerPath = '${quote(installerPath)}'
                finishedAt = (Get-Date).ToUniversalTime().ToString('o')
                message = [string]${'$'}message
            }
            ${'$'}json = ${'$'}payload | ConvertTo-Json -Compress
            [System.IO.File]::WriteAllText(
                ${'$'}result,
                ${'$'}json,
                (New-Object System.Text.UTF8Encoding(${'$'}false))
            )
            if (Test-Path -LiteralPath ${'$'}app) {
                Start-Process -FilePath ${'$'}app
            }
        """.trimIndent()
}
