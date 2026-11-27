package com.revibe.update

/**
 * SemVer 2.0 precedence, without build metadata (which never affects ordering).
 * Leading "v" is accepted because GitHub tags commonly include it.
 */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String> = emptyList(),
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        major.compareTo(other.major).takeIf { it != 0 }?.let { return it }
        minor.compareTo(other.minor).takeIf { it != 0 }?.let { return it }
        patch.compareTo(other.patch).takeIf { it != 0 }?.let { return it }
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
        if (preRelease.isEmpty()) return 1
        if (other.preRelease.isEmpty()) return -1
        val count = maxOf(preRelease.size, other.preRelease.size)
        for (index in 0 until count) {
            val left = preRelease.getOrNull(index) ?: return -1
            val right = other.preRelease.getOrNull(index) ?: return 1
            val leftNumber = left.toIntOrNull()
            val rightNumber = right.toIntOrNull()
            val result = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
            if (result != 0) return result
        }
        return 0
    }

    fun isNewerThan(other: SemanticVersion): Boolean = this > other

    override fun toString(): String = buildString {
        append(major).append('.').append(minor).append('.').append(patch)
        if (preRelease.isNotEmpty()) append('-').append(preRelease.joinToString("."))
    }

    companion object {
        private val pattern = Regex(
            """^[vV]?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""",
        )

        fun parseOrNull(raw: String): SemanticVersion? {
            val value = raw.trim()
            val match = pattern.matchEntire(value) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].toIntOrNull() ?: return null
            val pre = match.groupValues[4].takeIf { it.isNotBlank() }?.split('.') ?: emptyList()
            if (pre.any { it.isEmpty() }) return null
            return SemanticVersion(major, minor, patch, pre)
        }

        fun parse(raw: String): SemanticVersion =
            parseOrNull(raw) ?: throw UpdateException("Invalid semantic version: $raw")
    }
}
