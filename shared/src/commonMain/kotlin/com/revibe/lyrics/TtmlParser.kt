package com.revibe.lyrics

import kotlin.math.roundToLong

/**
 * Parser for the TTML used by Unison and other Apple-style lyric stores.
 *
 * Each `<p>` is a lyric line. `<span>` elements carry per-word begins. Text
 * between spans is preserved so adjacent words such as `No` + `thing` do not
 * become `No thing`; the UI renders word spans by concatenation.
 */
object TtmlParser {
    private val paragraph = Regex(
        """<p\b([^>]*)>(.*?)</p>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val span = Regex(
        """<span\b([^>]*)>(.*?)</span>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val beginAttr = Regex("""\bbegin\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val tag = Regex("""<[^>]+>""")
    private val whitespace = Regex("""\s+""")
    private val entity = Regex("""&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);""")

    fun parse(ttml: String?): List<LyricLine> {
        if (ttml.isNullOrBlank()) return emptyList()

        return paragraph.findAll(ttml).mapNotNull { match ->
            val attrs = match.groupValues[1]
            val content = match.groupValues[2]
            val lineStart = time(beginAttr.find(attrs)?.groupValues?.get(1)) ?: return@mapNotNull null
            val spans = span.findAll(content).toList()
            val words = mutableListOf<LyricWord>()

            spans.forEachIndexed { index, current ->
                val spanAttrs = current.groupValues[1]
                val rawText = decodeXml(stripTags(current.groupValues[2]))
                val trailingStart = current.range.last + 1
                val trailingEnd = if (index + 1 < spans.size) spans[index + 1].range.first else content.length
                val trailing = decodeXml(stripTags(content.substring(trailingStart, trailingEnd)))
                val wordText = rawText.trimStart() + trailing
                if (wordText.isNotEmpty()) {
                    words += LyricWord(
                        timeMs = time(beginAttr.find(spanAttrs)?.groupValues?.get(1)) ?: lineStart,
                        text = wordText,
                    )
                }
            }

            val text = decodeXml(stripTags(content)).replace(whitespace, " ").trim()
            if (text.isEmpty()) return@mapNotNull null
            LyricLine(timeMs = lineStart, text = text, words = words)
        }
            .sortedBy { it.timeMs }
            .distinctBy { it.timeMs to it.text }
            .toList()
    }

    /** Accepts `hh:mm:ss.mmm`, `mm:ss.mmm`, `ss.mmm` and a trailing `s`. */
    internal fun time(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        var value = raw.trim()
        if (value.endsWith("s", ignoreCase = true)) value = value.dropLast(1)
        val parts = value.replace(',', '.').split(':')
        val seconds = when (parts.size) {
            1 -> parts[0].toDoubleOrNull() ?: return null
            2 -> {
                val minutes = parts[0].toDoubleOrNull() ?: return null
                val secs = parts[1].toDoubleOrNull() ?: return null
                minutes * 60 + secs
            }
            3 -> {
                val hours = parts[0].toDoubleOrNull() ?: return null
                val minutes = parts[1].toDoubleOrNull() ?: return null
                val secs = parts[2].toDoubleOrNull() ?: return null
                hours * 3600 + minutes * 60 + secs
            }
            else -> return null
        }
        if (!seconds.isFinite() || seconds < 0) return null
        return (seconds * 1000).roundToLong()
    }

    private fun stripTags(value: String): String = value.replace(tag, "")

    private fun decodeXml(value: String): String = entity.replace(value) { match ->
        when (val code = match.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            else -> when {
                code.startsWith("#x", ignoreCase = true) ->
                    code.drop(2).toIntOrNull(16)?.let { Char(it) }?.toString() ?: match.value
                code.startsWith("#") ->
                    code.drop(1).toIntOrNull()?.let { Char(it) }?.toString() ?: match.value
                else -> match.value
            }
        }
    }
}
