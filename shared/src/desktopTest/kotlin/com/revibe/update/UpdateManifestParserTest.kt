package com.revibe.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UpdateManifestParserTest {
    @Test
    fun `parses all release manifest fields`() {
        val manifest = UpdateManifestParser.parse(
            """
            {
              "latestVersion": "0.10.0",
              "minimumVersion": "0.9.0",
              "downloadUrl": "https://github.com/revibe/revibe/releases/download/v0.10.0/Revibe-0.10.0.msi",
              "releaseNotes": "Faster sync\nUpdate checks",
              "mandatory": true,
              "sha256": "${"a".repeat(64)}",
              "publishedAt": "2026-11-26T00:00:00Z",
              "ignored": "allowed"
            }
            """.trimIndent(),
        )
        assertEquals("0.10.0", manifest.latestVersion)
        assertEquals("0.9.0", manifest.minimumVersion)
        assertTrue(manifest.mandatory)
        assertEquals("Faster sync\nUpdate checks", manifest.releaseNotes)
        assertEquals("2026-11-26T00:00:00Z", manifest.publishedAt)
    }

    @Test
    fun `optional fields use safe defaults`() {
        val manifest = UpdateManifestParser.parse("""{"latestVersion":"1.0.0"}""")
        assertEquals("", manifest.releaseNotes)
        assertEquals(false, manifest.mandatory)
        assertEquals(null, manifest.minimumVersion)
        assertEquals(null, manifest.sha256)
    }

    @Test
    fun `invalid json is a readable update error`() {
        assertFailsWith<UpdateException> {
            UpdateManifestParser.parse("not json")
        }
    }
}
