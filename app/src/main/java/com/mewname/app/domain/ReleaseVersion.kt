package com.mewname.app.domain

import java.math.BigInteger

/** Unknown tags never trigger a download recommendation; build metadata has no precedence. */
internal fun isNewerRelease(currentVersion: String, latestVersion: String): Boolean {
    val current = ReleaseVersion.parse(currentVersion) ?: return false
    val latest = ReleaseVersion.parse(latestVersion) ?: return false
    return latest > current
}

private data class ReleaseVersion(
    val numbers: List<BigInteger>,
    val prerelease: List<String>
) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        numbers.zip(other.numbers).forEach { (left, right) ->
            left.compareTo(right).takeIf { it != 0 }?.let { return it }
        }
        if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0
        if (prerelease.isEmpty()) return 1
        if (other.prerelease.isEmpty()) return -1
        prerelease.zip(other.prerelease).forEach { (left, right) ->
            val leftNumber = left.toBigIntegerOrNull()
            val rightNumber = right.toBigIntegerOrNull()
            val comparison = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
            if (comparison != 0) return comparison
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        private val pattern = Regex(
            """(\d+)\.(\d+)(?:\.(\d+))?(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?"""
        )

        fun parse(raw: String): ReleaseVersion? {
            val normalized = raw.trim().removePrefix("refs/tags/").removePrefix("v").removePrefix("V")
            val match = pattern.matchEntire(normalized) ?: return null
            return ReleaseVersion(
                (1..3).map { match.groupValues[it].ifEmpty { "0" }.toBigInteger() },
                match.groupValues[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
            )
        }
    }
}
