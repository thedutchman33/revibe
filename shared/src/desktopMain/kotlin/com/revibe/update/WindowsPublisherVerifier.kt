package com.revibe.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Uses Windows' own Authenticode verifier. A valid signature alone is not
 * enough: the signer subject must contain the publisher pinned at build time.
 */
class WindowsPublisherVerifier : PublisherVerifier {
    override suspend fun verify(path: String, expectedPublisher: String): PublisherCheck =
        withContext(Dispatchers.IO) {
            if (expectedPublisher.isBlank()) {
                return@withContext PublisherCheck(
                    PublisherStatus.INVALID,
                    message = "No publisher is pinned for this build; installation is blocked.",
                )
            }
            val file = File(path)
            if (!file.isFile) {
                return@withContext PublisherCheck(
                    PublisherStatus.UNAVAILABLE,
                    message = "The downloaded installer is missing.",
                )
            }
            if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
                return@withContext PublisherCheck(
                    PublisherStatus.UNAVAILABLE,
                    message = "Authenticode verification is only available on Windows.",
                )
            }

            val escapedPath = path.replace("'", "''")
            val script = """
                ${'$'}ErrorActionPreference = 'Stop'
                ${'$'}signature = Get-AuthenticodeSignature -LiteralPath '$escapedPath'
                if (${'$'}null -eq ${'$'}signature) { Write-Output 'Unavailable'; exit 0 }
                Write-Output ${'$'}signature.Status.ToString()
                if (${'$'}null -ne ${'$'}signature.SignerCertificate) {
                    Write-Output ${'$'}signature.SignerCertificate.Subject
                }
            """.trimIndent()

            val process = runCatching {
                ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-Command",
                    script,
                ).redirectErrorStream(true).start()
            }.getOrElse {
                return@withContext PublisherCheck(
                    PublisherStatus.UNAVAILABLE,
                    message = "Windows publisher verification could not be started.",
                )
            }
            val finished = process.waitFor(60, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext PublisherCheck(
                    PublisherStatus.UNAVAILABLE,
                    message = "Windows publisher verification timed out.",
                )
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val status = output.firstOrNull()
            if (!process.isAlive && process.exitValue() != 0 && status == null) {
                return@withContext PublisherCheck(
                    PublisherStatus.UNAVAILABLE,
                    message = "Windows publisher verification failed.",
                )
            }
            val subject = output.getOrNull(1)
            when {
                status.equals("Valid", ignoreCase = true) &&
                    subject?.contains(expectedPublisher, ignoreCase = true) == true -> PublisherCheck(
                    PublisherStatus.VALID,
                    subject = subject,
                )
                status.equals("Valid", ignoreCase = true) -> PublisherCheck(
                    PublisherStatus.INVALID,
                    subject = subject,
                    message = "The installer is signed by an unexpected publisher.",
                )
                status.equals("NotSigned", ignoreCase = true) -> PublisherCheck(
                    PublisherStatus.INVALID,
                    message = "The installer is not Authenticode signed.",
                )
                status == null -> PublisherCheck(
                    PublisherStatus.UNAVAILABLE,
                    message = "Windows publisher verification returned no result.",
                )
                else -> PublisherCheck(
                    PublisherStatus.INVALID,
                    message = "The installer signature is not valid ($status).",
                )
            }
        }
}
