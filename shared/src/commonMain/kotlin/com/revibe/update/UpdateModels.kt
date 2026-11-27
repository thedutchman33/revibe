package com.revibe.update

import kotlinx.serialization.Serializable

class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
data class UpdateManifest(
    val latestVersion: String,
    val minimumVersion: String? = null,
    val downloadUrl: String? = null,
    val releaseNotes: String = "",
    val mandatory: Boolean = false,
    val sha256: String? = null,
    val publishedAt: String? = null,
)

data class AvailableUpdate(
    val version: String,
    val minimumVersion: String?,
    val downloadUrl: String,
    val releaseNotes: String,
    val mandatory: Boolean,
    val sha256: String,
    val publishedAt: String?,
)

enum class UpdatePhase {
    DISABLED,
    IDLE,
    UP_TO_DATE,
    CHECKING,
    AVAILABLE,
    DOWNLOADING,
    VERIFYING,
    INSTALLING,
    INSTALLED,
    ERROR,
}

data class UpdateState(
    val phase: UpdatePhase,
    val currentVersion: String,
    val available: AvailableUpdate? = null,
    val message: String? = null,
    val progress: Float? = null,
    val lastCheckedAt: Long? = null,
    val notify: Boolean = false,
    val canSkip: Boolean = false,
    val canRemind: Boolean = false,
) {
    val isBusy: Boolean
        get() = phase == UpdatePhase.CHECKING ||
            phase == UpdatePhase.DOWNLOADING ||
            phase == UpdatePhase.VERIFYING ||
            phase == UpdatePhase.INSTALLING
}

@Serializable
data class StoredUpdateState(
    val skippedVersion: String? = null,
    val remindAfter: Long = 0L,
    val lastCheckedAt: Long = 0L,
)

@Serializable
data class InstallResult(
    val exitCode: Int = -1,
    val targetVersion: String = "",
    val installerPath: String = "",
    val finishedAt: String = "",
    val message: String = "",
) {
    val succeeded: Boolean
        get() = exitCode == 0 || exitCode == 3010
}

data class UpdateConfig(
    val currentVersion: String,
    val manifestUrl: String,
    val publisher: String,
    val updatesDirectory: String,
    /** Allows the unsigned GitHub Actions EXE release when no Authenticode pin exists. */
    val allowUnsignedInstaller: Boolean = false,
)

enum class PublisherStatus {
    VALID,
    INVALID,
    UNAVAILABLE,
}

data class PublisherCheck(
    val status: PublisherStatus,
    val subject: String? = null,
    val message: String? = null,
)

data class InstallHandoff(
    val accepted: Boolean,
    val message: String? = null,
)

interface UpdateManifestClient {
    suspend fun fetch(url: String): String
}

interface UpdateDownloader {
    /**
     * Downloads to [destinationPath] and returns the SHA-256 of the bytes written.
     * Implementations must only accept HTTPS and must leave no complete file on failure.
     */
    suspend fun download(
        url: String,
        destinationPath: String,
        onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit,
    ): String
}

interface UpdateFileHasher {
    suspend fun sha256(path: String): String?
}

interface PublisherVerifier {
    suspend fun verify(path: String, expectedPublisher: String): PublisherCheck
}

interface UpdateInstaller {
    /** Launches a separate Windows installer process; never executes before verification. */
    suspend fun launch(installerPath: String, targetVersion: String): InstallHandoff

    /** Removes a rejected/untrusted installer so it cannot be executed later. */
    suspend fun discard(installerPath: String)
}

interface UpdateStateStore {
    suspend fun load(): StoredUpdateState
    suspend fun save(state: StoredUpdateState)
    suspend fun readInstallResult(): InstallResult?
    suspend fun clearInstallResult()
}
