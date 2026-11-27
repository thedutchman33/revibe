package com.revibe

import com.revibe.lyrics.LrcParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogSmokeTest {

    @Test
    fun `lrc parser handles line and word sync`() {
        val synced = """
            [00:01.50]First line
            [00:03.20]<00:03.20>Hello <00:03.80>world
            [00:05.00]
        """.trimIndent()
        val lines = LrcParser.parse(synced)
        assertEquals(3, lines.size)
        assertEquals(1500, lines[0].timeMs)
        assertEquals("Hello world", lines[1].text)
        assertEquals(2, lines[1].words.size)
        assertTrue(LrcParser.parse(null).isEmpty())
    }
}
