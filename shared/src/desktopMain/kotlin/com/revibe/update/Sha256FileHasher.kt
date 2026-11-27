package com.revibe.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class Sha256FileHasher : UpdateFileHasher {
    override suspend fun sha256(path: String): String? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.isFile) return@withContext null
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }.getOrNull()
    }
}
