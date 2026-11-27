package com.revibe.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemanticVersionTest {
    @Test
    fun `numeric versions compare correctly`() {
        assertTrue(SemanticVersion.parse("0.10.0") > SemanticVersion.parse("0.9.9"))
        assertTrue(SemanticVersion.parse("v1.2.3") > SemanticVersion.parse("1.2.2"))
        assertEquals(SemanticVersion.parse("1.2.3+build.7"), SemanticVersion.parse("1.2.3"))
    }

    @Test
    fun `prerelease precedence follows semver`() {
        assertTrue(SemanticVersion.parse("1.0.0") > SemanticVersion.parse("1.0.0-rc.1"))
        assertTrue(SemanticVersion.parse("1.0.0-rc.2") > SemanticVersion.parse("1.0.0-rc.1"))
        assertTrue(SemanticVersion.parse("1.0.0-alpha.1") > SemanticVersion.parse("1.0.0-alpha"))
        assertTrue(SemanticVersion.parse("1.0.0-alpha.beta") > SemanticVersion.parse("1.0.0-alpha.1"))
    }

    @Test
    fun `invalid versions are rejected`() {
        assertNull(SemanticVersion.parseOrNull("1.2"))
        assertNull(SemanticVersion.parseOrNull("1.2.3.4"))
        assertNull(SemanticVersion.parseOrNull("latest"))
        assertNull(SemanticVersion.parseOrNull("1.2.3-"))
    }
}
