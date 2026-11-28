package com.revibe.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Owns update policy and state. Network work runs on [scope], never on the UI
 * thread; playback is never paused or blocked by an available update.
 */
class UpdateService(
    private val config: UpdateConfig,
    private val manifestClient: UpdateManifestClient,
    private val downloader: UpdateDownloader,
    private val hasher: UpdateFileHasher,
    private val publisherVerifier: PublisherVerifier,
    private val installer: UpdateInstaller,
    private val stateStore: UpdateStateStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val startupDelayMs: Long = 10_000L,
    private val checkIntervalMs: Long = 6L * 60L * 60L * 1_000L,
    private val remindCooldownMs: Long = 24L * 60L * 60L * 1_000L,
) {
    private val currentVersion = SemanticVersion.parseOrNull(config.currentVersion)
    private val initial = when {
        currentVersion == null -> UpdateState(
            phase = UpdatePhase.ERROR,
            currentVersion = config.currentVersion,
            message = "This build has an invalid version number.",
        )
        config.manifestUrl.isBlank() -> UpdateState(
            phase = UpdatePhase.DISABLED,
            currentVersion = config.currentVersion,
            message = "Update checks are not configured for this build.",
        )
        else -> UpdateState(
            phase = UpdatePhase.IDLE,
            currentVersion = config.currentVersion,
        )
    }
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val checkMutex = Mutex()
    private val installMutex = Mutex()
    private var started = false
    private var loopJob: Job? = null
    private var rememberedSkip: String? = null
    private var rememberedRemind: Long = 0L

    /** Installed by the desktop main window so a launched installer can close the app safely. */
    var onShutdownRequested: (() -> Unit)? = null

    fun start() {
        if (started) return
        started = true
        loopJob = scope.launch {
            val stored = runCatching { stateStore.load() }.getOrDefault(StoredUpdateState())
            rememberedSkip = stored.skippedVersion
            rememberedRemind = stored.remindAfter
            _state.value = _state.value.copy(
                lastCheckedAt = stored.lastCheckedAt.takeIf { it > 0L },
            )
            recoverInstallResult()
            if (config.manifestUrl.isBlank() || currentVersion == null) return@launch
            if (startupDelayMs > 0L) delay(startupDelayMs)
            checkForUpdates(manual = false)
            while (isActive && checkIntervalMs > 0L) {
                delay(checkIntervalMs)
                checkForUpdates(manual = false)
            }
        }
    }

    fun checkNow() {
        scope.launch { checkForUpdates(manual = true) }
    }

    fun installNow() {
        scope.launch { installUpdate() }
    }

    /**
     * Fetches the manifest and updates [state]. A manual check bypasses a skip or
     * a "remind me later" suppression, but never bypasses mandatory policy.
     */
    suspend fun checkForUpdates(manual: Boolean): Boolean {
        if (currentVersion == null || config.manifestUrl.isBlank()) return false
        // Keep the post-install confirmation visible until the user dismisses it.
        if (_state.value.phase == UpdatePhase.INSTALLED) return false
        if (_state.value.phase in BUSY_PHASES) return false
        if (!checkMutex.tryLock()) return false
        try {
            _state.value = _state.value.copy(
                phase = UpdatePhase.CHECKING,
                message = null,
                progress = null,
                notify = false,
            )
            val raw = manifestClient.fetch(config.manifestUrl)
            if (raw.length > MAX_MANIFEST_BYTES) {
                throw UpdateException("The update manifest is too large.")
            }
            val manifest = UpdateManifestParser.parse(raw)
            val latest = SemanticVersion.parse(manifest.latestVersion)
            val minimum = manifest.minimumVersion
                ?.takeIf { it.isNotBlank() }
                ?.let { SemanticVersion.parse(it) }
            if (minimum != null && latest < minimum) {
                throw UpdateException("The update manifest has an invalid version range.")
            }

            val now = clock()
            val stored = runCatching { stateStore.load() }.getOrDefault(StoredUpdateState())
            val checked = stored.copy(lastCheckedAt = now)
            runCatching { stateStore.save(checked) }

            if (latest <= currentVersion) {
                _state.value = UpdateState(
                    phase = UpdatePhase.UP_TO_DATE,
                    currentVersion = config.currentVersion,
                    message = "You're on the latest version.",
                    lastCheckedAt = now,
                )
                return true
            }

            val available = validateAvailable(manifest, latest, minimum)
            val mandatory = manifest.mandatory || (minimum != null && currentVersion < minimum)
            val skipped = (stored.skippedVersion ?: rememberedSkip) == available.version
            val reminded = maxOf(stored.remindAfter, rememberedRemind) > now
            val notify = mandatory || manual || (!skipped && !reminded)
            _state.value = UpdateState(
                phase = UpdatePhase.AVAILABLE,
                currentVersion = config.currentVersion,
                available = available,
                message = when {
                    mandatory -> "This update is required."
                    skipped -> "You skipped this version."
                    reminded -> "Reminder paused."
                    else -> "Version ${available.version} is available."
                },
                lastCheckedAt = now,
                notify = notify,
                canSkip = !mandatory,
                canRemind = !mandatory,
            )
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val fallback = when (error) {
                is UpdateException -> error.message
                else -> "Could not check for updates."
            } ?: "Could not check for updates."
            _state.value = _state.value.copy(
                phase = UpdatePhase.ERROR,
                message = fallback,
                progress = null,
                notify = _state.value.available != null,
            )
            return false
        } finally {
            checkMutex.unlock()
        }
    }

    suspend fun installUpdate(): Boolean {
        if (_state.value.phase in BUSY_PHASES) return false
        if (!installMutex.tryLock()) return false
        try {
            var available = _state.value.available
            if (available == null) {
                checkForUpdates(manual = true)
                available = _state.value.available
            }
            val update = available ?: return false
            if (config.publisher.isBlank() && !config.allowUnsignedInstaller) {
                throw UpdateException(
                    "This build has no pinned publisher. Unsigned automatic installation is disabled.",
                )
            }
            if (!update.downloadUrl.startsWith("https://", ignoreCase = true)) {
                throw UpdateException("The installer URL is not HTTPS.")
            }
            val installerExtension = windowsInstallerExtension(update.downloadUrl)
                ?: throw UpdateException("This release does not provide a Windows EXE or MSI installer.")

            val safeVersion = update.version.replace(Regex("[^0-9A-Za-z._-]"), "_")
            val destination =
                "${config.updatesDirectory.trimEnd('/', '\\')}/downloads/Revibe-$safeVersion.$installerExtension"
            _state.value = _state.value.copy(
                phase = UpdatePhase.DOWNLOADING,
                available = update,
                message = "Downloading Revibe ${update.version}…",
                progress = 0f,
                notify = true,
            )

            var lastPercent = -1
            val downloadedHash = downloader.download(update.downloadUrl, destination) { received, total ->
                val percent = total
                    ?.takeIf { it > 0L }
                    ?.let { ((received.toDouble() / it.toDouble()) * 100.0).toInt().coerceIn(0, 100) }
                if (percent != null && percent != lastPercent) {
                    lastPercent = percent
                    _state.value = _state.value.copy(
                        phase = UpdatePhase.DOWNLOADING,
                        progress = percent / 100f,
                        message = "Downloading Revibe ${update.version}… $percent%",
                    )
                }
            }

            _state.value = _state.value.copy(
                phase = UpdatePhase.VERIFYING,
                progress = null,
                message = "Verifying download integrity…",
            )
            val hashedOnDisk = hasher.sha256(destination)
            if (hashedOnDisk == null) {
                installer.discard(destination)
                throw UpdateException("The downloaded installer could not be read.")
            }
            if (!hashedOnDisk.equals(update.sha256, ignoreCase = true) ||
                !downloadedHash.equals(update.sha256, ignoreCase = true)
            ) {
                installer.discard(destination)
                throw UpdateException(
                    "The downloaded installer failed its SHA-256 check and was discarded.",
                )
            }

            if (config.publisher.isNotBlank()) {
                _state.value = _state.value.copy(
                    phase = UpdatePhase.VERIFYING,
                    message = "Verifying the Windows publisher signature…",
                )
                val publisher = publisherVerifier.verify(destination, config.publisher)
                if (publisher.status != PublisherStatus.VALID) {
                    installer.discard(destination)
                    throw UpdateException(
                        publisher.message
                            ?: "The installer signature could not be verified and was discarded.",
                    )
                }
            } else {
                _state.value = _state.value.copy(
                    phase = UpdatePhase.VERIFYING,
                    message = "Verifying the unsigned installer by SHA-256…",
                )
            }
            // Re-hash after the signature check so a changed file is never launched.
            if (!hasher.sha256(destination).equals(update.sha256, ignoreCase = true)) {
                installer.discard(destination)
                throw UpdateException("The installer changed after verification and was discarded.")
            }

            _state.value = _state.value.copy(
                phase = UpdatePhase.INSTALLING,
                progress = null,
                message = "Closing Revibe and running the verified installer…",
                notify = true,
            )
            val handoff = installer.launch(destination, update.version)
            if (!handoff.accepted) {
                installer.discard(destination)
                throw UpdateException(handoff.message ?: "The installer could not be started.")
            }
            onShutdownRequested?.invoke()
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: LinkageError) {
            // A packaged runtime missing java.net.http used to kill this coroutine
            // with NoClassDefFoundError and leave the UI on "Downloading 0%".
            _state.value = _state.value.copy(
                phase = UpdatePhase.ERROR,
                message = "The updater is missing a required runtime component. " +
                    "Reinstall Revibe from the official installer and try again.",
                progress = null,
                notify = true,
            )
            return false
        } catch (error: Exception) {
            val message = when (error) {
                is UpdateException -> error.message
                else -> "The update could not be installed."
            } ?: "The update could not be installed."
            _state.value = _state.value.copy(
                phase = UpdatePhase.ERROR,
                message = message,
                progress = null,
                notify = true,
            )
            return false
        } finally {
            installMutex.unlock()
        }
    }

    fun remindLater() {
        val current = _state.value
        val update = current.available ?: return
        if (update.mandatory) return
        rememberedRemind = clock() + remindCooldownMs
        _state.value = current.copy(
            notify = false,
            message = "We'll remind you about Revibe ${update.version} later.",
        )
        scope.launch {
            val stored = runCatching { stateStore.load() }.getOrDefault(StoredUpdateState())
            runCatching { stateStore.save(stored.copy(remindAfter = clock() + remindCooldownMs)) }
        }
    }

    fun skipVersion() {
        val current = _state.value
        val update = current.available ?: return
        if (update.mandatory) return
        rememberedSkip = update.version
        _state.value = current.copy(
            notify = false,
            message = "You skipped Revibe ${update.version}.",
        )
        scope.launch {
            val stored = runCatching { stateStore.load() }.getOrDefault(StoredUpdateState())
            runCatching { stateStore.save(stored.copy(skippedVersion = update.version)) }
        }
    }

    /** Dismisses a completed-update confirmation or a non-mandatory notice. */
    fun dismissNotice() {
        val current = _state.value
        if (current.phase == UpdatePhase.INSTALLED) {
            _state.value = current.copy(
                phase = UpdatePhase.IDLE,
                message = null,
                notify = false,
            )
        } else if (!current.available?.mandatory.orFalse()) {
            _state.value = current.copy(notify = false)
        }
    }

    fun shutdownLoop() {
        loopJob?.cancel()
        loopJob = null
        started = false
    }

    private fun validateAvailable(
        manifest: UpdateManifest,
        latest: SemanticVersion,
        minimum: SemanticVersion?,
    ): AvailableUpdate {
        val downloadUrl = manifest.downloadUrl
            ?.takeIf { it.startsWith("https://", ignoreCase = true) }
            ?: throw UpdateException("The update has no HTTPS download URL.")
        if (windowsInstallerExtension(downloadUrl) == null) {
            throw UpdateException("The update does not provide a Windows EXE or MSI installer.")
        }
        val sha = manifest.sha256
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.matches(Regex("^[0-9a-f]{64}$")) }
            ?: throw UpdateException("The update has no valid SHA-256 checksum.")
        return AvailableUpdate(
            version = latest.toString(),
            minimumVersion = minimum?.toString(),
            downloadUrl = downloadUrl,
            releaseNotes = manifest.releaseNotes.trim(),
            mandatory = manifest.mandatory || (minimum != null && currentVersion!! < minimum),
            sha256 = sha,
            publishedAt = manifest.publishedAt?.takeIf { it.isNotBlank() },
        )
    }

    internal suspend fun recoverInstallResult() {
        val result = runCatching { stateStore.readInstallResult() }.getOrNull() ?: return
        runCatching { stateStore.clearInstallResult() }
        val target = SemanticVersion.parseOrNull(result.targetVersion)
        val installed = currentVersion
        if (result.succeeded && target != null && installed != null && installed >= target) {
            if (result.installerPath.isNotBlank()) installer.discard(result.installerPath)
            _state.value = _state.value.copy(
                phase = UpdatePhase.INSTALLED,
                available = null,
                message = "Updated to Revibe ${result.targetVersion}.",
                progress = null,
                notify = true,
            )
        } else if (result.succeeded) {
            _state.value = _state.value.copy(
                phase = UpdatePhase.ERROR,
                message = "The installer finished, but Revibe is still version ${config.currentVersion}.",
                progress = null,
                notify = true,
            )
        } else {
            val detail = result.message
                .takeIf { it.isNotBlank() }
                ?: "No installer detail was provided."
            _state.value = _state.value.copy(
                phase = UpdatePhase.ERROR,
                message = "Installation did not complete (exit code ${result.exitCode}). $detail",
                progress = null,
                notify = true,
            )
        }
    }

    private fun Boolean?.orFalse(): Boolean = this == true

    private companion object {
        const val MAX_MANIFEST_BYTES = 1_000_000
        val BUSY_PHASES = setOf(
            UpdatePhase.CHECKING,
            UpdatePhase.DOWNLOADING,
            UpdatePhase.VERIFYING,
            UpdatePhase.INSTALLING,
        )
    }
}

/** Returns the supported installer extension, or null for an unsafe or unsupported URL. */
internal fun windowsInstallerExtension(url: String): String? {
    val path = url.substringBefore('?').substringBefore('#')
    return when {
        path.endsWith(".exe", ignoreCase = true) -> "exe"
        path.endsWith(".msi", ignoreCase = true) -> "msi"
        else -> null
    }
}
