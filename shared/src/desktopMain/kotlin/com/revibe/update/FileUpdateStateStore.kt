package com.revibe.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FileUpdateStateStore(updatesDirectory: File) : UpdateStateStore {
    private val directory = updatesDirectory.apply { mkdirs() }
    private val stateFile = File(directory, "state.json")
    private val resultFile = File(directory, "install-result.json")
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun load(): StoredUpdateState = withContext(Dispatchers.IO) {
        if (!stateFile.isFile) return@withContext StoredUpdateState()
        runCatching {
            json.decodeFromString(StoredUpdateState.serializer(), stateFile.readText())
        }.getOrDefault(StoredUpdateState())
    }

    override suspend fun save(state: StoredUpdateState) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val temp = File(directory, "state.json.tmp")
        temp.writeText(json.encodeToString(StoredUpdateState.serializer(), state))
        moveReplacing(temp, stateFile)
    }

    override suspend fun readInstallResult(): InstallResult? = withContext(Dispatchers.IO) {
        if (!resultFile.isFile) return@withContext null
        runCatching {
            json.decodeFromString(InstallResult.serializer(), resultFile.readText().removePrefix("\uFEFF"))
        }.getOrNull()
    }

    override suspend fun clearInstallResult() = withContext(Dispatchers.IO) {
        resultFile.delete()
        Unit
    }

    private fun moveReplacing(source: File, target: File) {
        runCatching {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.onFailure {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
