package com.revibe.imports

/** Native file selection. Reading and ZIP extraction stay on the device. */
interface ImportFilePicker {
    suspend fun pickPlaylistFile(): PickedTextFile?
    suspend fun pickSpotifyArchive(): SpotifyArchiveEntries?

    /** Opens the OS Downloads folder so the user can find the export themselves. */
    suspend fun openDownloadsFolder(): Boolean = false
}
