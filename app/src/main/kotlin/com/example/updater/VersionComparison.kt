package com.example.updater

object VersionComparison {
    fun isNewer(remote: String, current: String): Boolean {
        fun parse(value: String): List<Int>? {
            if (!value.matches(Regex("[0-9]+(\\.[0-9]+)*"))) return null
            return value.split('.').map { it.toIntOrNull() ?: return null }
        }
        val remoteParts = parse(remote) ?: return false
        val currentParts = parse(current) ?: return false
        for (index in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val difference = (remoteParts.getOrNull(index) ?: 0).compareTo(currentParts.getOrNull(index) ?: 0)
            if (difference != 0) return difference > 0
        }
        return false
    }
}
