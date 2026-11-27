package com.revibe.account

import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Session tokens are encrypted with the current Windows user's DPAPI key before
 * touching disk. If DPAPI is unavailable the store degrades to memory-only
 * behaviour instead of writing plaintext credentials.
 */
class DpapiAuthSessionStore(private val file: File) : AuthSessionStore {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override suspend fun load(): StoredSession? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        runCatching {
            val decrypted = Crypt32Util.cryptUnprotectData(file.readBytes())
            json.decodeFromString<StoredSession>(decrypted.decodeToString())
        }.getOrNull()
    }

    override suspend fun save(session: StoredSession) = withContext(Dispatchers.IO) {
        val plain = json.encodeToString(StoredSession.serializer(), session).encodeToByteArray()
        val encrypted = Crypt32Util.cryptProtectData(plain)
        val parent = file.parentFile
        if (parent != null) parent.mkdirs()
        val temp = File(file.absolutePath + ".tmp")
        temp.writeBytes(encrypted)
        runCatching {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        Unit
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        File(file.absolutePath + ".tmp").delete()
        Unit
    }
}
