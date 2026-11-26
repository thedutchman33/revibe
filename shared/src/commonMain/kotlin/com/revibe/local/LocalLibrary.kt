package com.revibe.local

import com.revibe.model.ScanReport
import com.revibe.model.Track

/**
 * Local files remain fully usable offline. Scanning and tag reading are platform
 * specific; the rest of the app only sees tracks.
 */
interface LocalLibrary {
    suspend fun folders(): List<String>
    suspend fun addFolder(path: String)
    suspend fun removeFolder(path: String)
    suspend fun tracks(): List<Track>
    suspend fun scan(onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }): ScanReport
}
