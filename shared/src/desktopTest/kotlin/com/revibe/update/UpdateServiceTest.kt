package com.revibe.update

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateServiceTest {
    private val goodSha = "a".repeat(64)
    private val downloadUrl =
        "https://github.com/revibe/revibe/releases/download/v0.10.0/Revibe-0.10.0.msi"
    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("revibe-update-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `unconfigured build reports disabled without a request`() = runTest {
        val harness = harness(manifestUrl = "")
        assertEquals(UpdatePhase.DISABLED, harness.service.state.value.phase)
        assertFalse(harness.service.checkForUpdates(manual = true))
        assertEquals(0, harness.manifestClient.calls)
    }

    @Test
    fun `optional update exposes release notes and controls`() = runTest {
        val harness = harness()
        assertTrue(harness.service.checkForUpdates(manual = true))
        val state = harness.service.state.value
        assertEquals(UpdatePhase.AVAILABLE, state.phase)
        assertEquals("0.10.0", state.available?.version)
        assertEquals("Faster sync and safer updates.", state.available?.releaseNotes)
        assertFalse(state.available?.mandatory == true)
        assertTrue(state.canSkip)
        assertTrue(state.canRemind)
        assertTrue(state.notify)
    }

    @Test
    fun `installed version below minimum is mandatory`() = runTest {
        val harness = harness(
            manifest = manifestJson(minimum = "0.10.0"),
        )
        assertTrue(harness.service.checkForUpdates(manual = false))
        val state = harness.service.state.value
        assertEquals(UpdatePhase.AVAILABLE, state.phase)
        assertTrue(state.available?.mandatory == true)
        assertFalse(state.canSkip)
        assertFalse(state.canRemind)
        harness.service.skipVersion()
        harness.service.remindLater()
        advanceUntilIdle()
        assertTrue(harness.service.state.value.notify)
        assertEquals(UpdatePhase.AVAILABLE, harness.service.state.value.phase)
    }

    @Test
    fun `same version is up to date`() = runTest {
        val harness = harness(manifest = manifestJson(version = "0.9.0", minimum = "0.9.0"))
        assertTrue(harness.service.checkForUpdates(manual = true))
        assertEquals(UpdatePhase.UP_TO_DATE, harness.service.state.value.phase)
        assertNull(harness.service.state.value.available)
    }

    @Test
    fun `invalid checksum in manifest fails safely`() = runTest {
        val harness = harness(manifest = manifestJson(sha = "not-a-sha"))
        assertFalse(harness.service.checkForUpdates(manual = true))
        assertEquals(UpdatePhase.ERROR, harness.service.state.value.phase)
        assertTrue(harness.service.state.value.message.orEmpty().contains("SHA-256"))
    }

    @Test
    fun `network failure is reported without throwing`() = runTest {
        val harness = harness()
        harness.manifestClient.error = UpdateException("Could not reach the update server.")
        assertFalse(harness.service.checkForUpdates(manual = true))
        assertEquals(UpdatePhase.ERROR, harness.service.state.value.phase)
        assertTrue(harness.service.state.value.message.orEmpty().contains("reach"))
    }

    @Test
    fun `skip suppresses automatic notice but manual checks still show it`() = runTest {
        val harness = harness()
        assertTrue(harness.service.checkForUpdates(manual = false))
        harness.service.skipVersion()
        advanceUntilIdle()
        assertFalse(harness.service.state.value.notify)

        assertTrue(harness.service.checkForUpdates(manual = false))
        assertFalse(harness.service.state.value.notify)
        assertTrue(harness.service.checkForUpdates(manual = true))
        assertTrue(harness.service.state.value.notify)
    }

    @Test
    fun `remind later suppresses automatic notice until manual check`() = runTest {
        val harness = harness()
        assertTrue(harness.service.checkForUpdates(manual = false))
        harness.service.remindLater()
        advanceUntilIdle()
        assertFalse(harness.service.state.value.notify)

        assertTrue(harness.service.checkForUpdates(manual = false))
        assertFalse(harness.service.state.value.notify)
        assertTrue(harness.service.checkForUpdates(manual = true))
        assertTrue(harness.service.state.value.notify)
    }

    @Test
    fun `verified download launches installer and requests shutdown`() = runTest {
        val harness = harness()
        assertTrue(harness.service.checkForUpdates(manual = true))
        var shutdown = false
        harness.service.onShutdownRequested = { shutdown = true }

        assertTrue(harness.service.installUpdate())
        assertTrue(harness.installer.launched)
        assertTrue(shutdown)
        assertEquals(UpdatePhase.INSTALLING, harness.service.state.value.phase)
        assertEquals(0, harness.installer.discarded)
        assertEquals(2, (harness.downloader as FakeDownloader).progressCalls)
    }

    @Test
    fun `checksum mismatch discards installer`() = runTest {
        val harness = harness()
        assertTrue(harness.service.checkForUpdates(manual = true))
        (harness.downloader as FakeDownloader).hash = "b".repeat(64)

        assertFalse(harness.service.installUpdate())
        assertEquals(UpdatePhase.ERROR, harness.service.state.value.phase)
        assertFalse(harness.installer.launched)
        assertTrue(harness.installer.discarded > 0)
    }

    @Test
    fun `invalid publisher discards installer`() = runTest {
        val harness = harness(verifierStatus = PublisherStatus.INVALID)
        assertTrue(harness.service.checkForUpdates(manual = true))

        assertFalse(harness.service.installUpdate())
        assertEquals(UpdatePhase.ERROR, harness.service.state.value.phase)
        assertFalse(harness.installer.launched)
        assertTrue(harness.installer.discarded > 0)
    }

    @Test
    fun `interrupted download reports error and never launches`() = runTest {
        val harness = harness()
        assertTrue(harness.service.checkForUpdates(manual = true))
        (harness.downloader as FakeDownloader).error =
            UpdateException("The update download was interrupted.")

        assertFalse(harness.service.installUpdate())
        assertEquals(UpdatePhase.ERROR, harness.service.state.value.phase)
        assertFalse(harness.installer.launched)
    }

    @Test
    fun `installer rejection is reported and discarded`() = runTest {
        val harness = harness(installerAccepted = false)
        assertTrue(harness.service.checkForUpdates(manual = true))

        assertFalse(harness.service.installUpdate())
        assertEquals(UpdatePhase.ERROR, harness.service.state.value.phase)
        assertTrue(harness.installer.discarded > 0)
    }

    @Test
    fun `download progress is visible before verification`() = runTest {
        val downloader = HoldingDownloader()
        val harness = harness(downloader = downloader)
        assertTrue(harness.service.checkForUpdates(manual = true))
        val job = backgroundScope.launch { harness.service.installUpdate() }
        runCurrent()

        assertEquals(UpdatePhase.DOWNLOADING, harness.service.state.value.phase)
        assertEquals(0.5f, harness.service.state.value.progress)
        job.cancelAndJoin()
    }

    @Test
    fun `successful install result is confirmed on next launch`() = runTest {
        val store = FakeStore(
            result = InstallResult(
                exitCode = 0,
                targetVersion = "0.10.0",
                installerPath = File(tempDir, "downloads/Revibe-0.10.0.msi").absolutePath,
            ),
        )
        val harness = harness(version = "0.10.0", store = store, manifestUrl = "")
        harness.service.recoverInstallResult()

        assertEquals(UpdatePhase.INSTALLED, harness.service.state.value.phase)
        assertTrue(harness.service.state.value.message.orEmpty().contains("0.10.0"))
        assertNull(store.result)
        assertTrue(harness.installer.discarded > 0)
    }

    @Test
    fun `failed install result is recoverable`() = runTest {
        val store = FakeStore(
            result = InstallResult(
                exitCode = 1603,
                targetVersion = "0.10.0",
                message = "Fatal error during installation.",
            ),
        )
        val harness = harness(store = store, manifestUrl = "")
        harness.service.recoverInstallResult()

        assertEquals(UpdatePhase.ERROR, harness.service.state.value.phase)
        assertTrue(harness.service.state.value.message.orEmpty().contains("1603"))
        assertNull(store.result)
    }

    @Test
    fun `update flow preserves the database, session and local library references`() = runTest {
        val database = File(tempDir, "revibe.db")
        database.writeText("existing sqlite bytes")
        val session = File(tempDir, "account.session").apply { writeText("encrypted session") }
        val local = File(tempDir, "music/local track.mp3").apply {
            parentFile?.mkdirs()
            writeText("local audio reference")
        }
        val harness = harness()
        assertTrue(harness.service.checkForUpdates(manual = true))
        assertTrue(harness.service.installUpdate())

        assertEquals("existing sqlite bytes", database.readText())
        assertEquals("encrypted session", session.readText())
        assertEquals("local audio reference", local.readText())
    }

    private fun TestScope.harness(
        version: String = "0.9.0",
        manifest: String = manifestJson(),
        manifestUrl: String = "https://updates.example.test/manifest.json",
        store: FakeStore = FakeStore(),
        downloader: UpdateDownloader = FakeDownloader(goodSha),
        verifierStatus: PublisherStatus = PublisherStatus.VALID,
        installerAccepted: Boolean = true,
    ): Harness {
        val manifestClient = FakeManifestClient().apply { response = manifest }
        val hasher = FakeHasher(goodSha)
        val verifier = FakeVerifier(verifierStatus)
        val installer = FakeInstaller(installerAccepted)
        val config = UpdateConfig(
            currentVersion = version,
            manifestUrl = manifestUrl,
            publisher = "CN=Revibe, O=Revibe",
            updatesDirectory = tempDir.absolutePath,
        )
        val service = UpdateService(
            config = config,
            manifestClient = manifestClient,
            downloader = downloader,
            hasher = hasher,
            publisherVerifier = verifier,
            installer = installer,
            stateStore = store,
            scope = backgroundScope,
            clock = { 1_000_000L },
            startupDelayMs = 0L,
            checkIntervalMs = 0L,
        )
        return Harness(service, store, manifestClient, hasher, installer, downloader)
    }

    private fun manifestJson(
        version: String = "0.10.0",
        minimum: String = "0.9.0",
        mandatory: Boolean = false,
        sha: String = goodSha,
        url: String = downloadUrl,
    ): String = """
        {
          "latestVersion": "$version",
          "minimumVersion": "$minimum",
          "downloadUrl": "$url",
          "releaseNotes": "Faster sync and safer updates.",
          "mandatory": $mandatory,
          "sha256": "$sha",
          "publishedAt": "2026-11-26T00:00:00Z"
        }
    """.trimIndent()

    private data class Harness(
        val service: UpdateService,
        val store: FakeStore,
        val manifestClient: FakeManifestClient,
        val hasher: FakeHasher,
        val installer: FakeInstaller,
        val downloader: UpdateDownloader,
    )

    private class FakeManifestClient : UpdateManifestClient {
        var response: String = ""
        var error: Throwable? = null
        var calls = 0

        override suspend fun fetch(url: String): String {
            calls++
            error?.let { throw it }
            return response
        }
    }

    private class FakeDownloader(initialHash: String) : UpdateDownloader {
        var hash: String = initialHash
        var error: Throwable? = null
        var progressCalls = 0

        override suspend fun download(
            url: String,
            destinationPath: String,
            onProgress: (Long, Long?) -> Unit,
        ): String {
            error?.let { throw it }
            File(destinationPath).parentFile?.mkdirs()
            File(destinationPath).writeText("verified installer")
            progressCalls++
            onProgress(50L, 100L)
            progressCalls++
            onProgress(100L, 100L)
            return hash
        }
    }

    private class HoldingDownloader : UpdateDownloader {
        override suspend fun download(
            url: String,
            destinationPath: String,
            onProgress: (Long, Long?) -> Unit,
        ): String {
            onProgress(50L, 100L)
            awaitCancellation()
        }
    }

    private class FakeHasher(var hash: String) : UpdateFileHasher {
        override suspend fun sha256(path: String): String? = hash
    }

    private class FakeVerifier(private val status: PublisherStatus) : PublisherVerifier {
        override suspend fun verify(path: String, expectedPublisher: String): PublisherCheck =
            PublisherCheck(status, subject = "CN=Revibe, O=Revibe")
    }

    private class FakeInstaller(private val accepted: Boolean) : UpdateInstaller {
        var launched = false
        var discarded = 0

        override suspend fun launch(installerPath: String, targetVersion: String): InstallHandoff {
            launched = true
            return InstallHandoff(accepted, if (accepted) null else "Installer rejected.")
        }

        override suspend fun discard(installerPath: String) {
            discarded++
            File(installerPath).delete()
        }
    }

    private class FakeStore(var result: InstallResult? = null) : UpdateStateStore {
        var stored = StoredUpdateState()

        override suspend fun load(): StoredUpdateState = stored

        override suspend fun save(state: StoredUpdateState) {
            stored = state
        }

        override suspend fun readInstallResult(): InstallResult? = result

        override suspend fun clearInstallResult() {
            result = null
        }
    }
}
