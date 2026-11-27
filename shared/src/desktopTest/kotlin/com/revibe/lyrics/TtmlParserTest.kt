package com.revibe.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TtmlParserTest {
    @Test
    fun `parses line and word timings without inserting spaces between adjacent spans`() {
        val ttml = """
            <tt><body><div>
              <p begin="2:30.140" end="2:31.738"><span begin="2:30.140">Step</span> <span begin="2:30.424">out</span> <span begin="2:30.638">through</span></p>
              <p begin="2:33.757"><span begin="2:33.757">No</span><span begin="2:33.963">thing</span></p>
            </div></body></tt>
        """.trimIndent()

        val lines = TtmlParser.parse(ttml)

        assertEquals(2, lines.size)
        assertEquals(150_140, lines[0].timeMs)
        assertEquals("Step out through", lines[0].text)
        assertEquals(listOf("Step ", "out ", "through"), lines[0].words.map { it.text })
        assertEquals(150_140, lines[0].words.first().timeMs)
        assertEquals(150_424, lines[0].words[1].timeMs)
        assertEquals("Nothing", lines[1].text)
        assertEquals(listOf("No", "thing"), lines[1].words.map { it.text })
        assertTrue(lines.all { it.words.isNotEmpty() })
    }

    @Test
    fun `keeps line-only lyrics and decodes XML entities`() {
        val ttml = """
            <tt><body><div>
              <p begin="1.5">Rock &amp; Roll &#39;n&#39; Roll</p>
              <p begin="2.5">Don&apos;t stop</p>
            </div></body></tt>
        """.trimIndent()

        val lines = TtmlParser.parse(ttml)

        assertEquals(2, lines.size)
        assertEquals("Rock & Roll 'n' Roll", lines[0].text)
        assertEquals("Don't stop", lines[1].text)
        assertTrue(lines.all { it.words.isEmpty() })
    }

    @Test
    fun `accepts hours and milliseconds and rejects bad timestamps`() {
        assertEquals(3_723_500, TtmlParser.time("1:02:03.500"))
        assertEquals(90_250, TtmlParser.time("1:30.25"))
        assertEquals(12_500, TtmlParser.time("12.5s"))
        assertEquals(null, TtmlParser.time("nope"))

        val lines = TtmlParser.parse("""<p begin="bad">Ignored</p><p begin="00:03.000">Kept</p>""")
        assertEquals(listOf("Kept"), lines.map { it.text })
    }

    @Test
    fun `returns no lines for empty or non-TTML payloads`() {
        assertTrue(TtmlParser.parse(null).isEmpty())
        assertTrue(TtmlParser.parse("").isEmpty())
        assertTrue(TtmlParser.parse("not xml at all").isEmpty())
    }
}
