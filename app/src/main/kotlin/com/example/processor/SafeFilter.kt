package com.example.processor

import com.google.re2j.Pattern

object SafeFilter {
    fun matches(filter: String, sender: String, body: String): Boolean {
        val value = filter.trim()
        if (value.isEmpty()) return true
        if (value.length > 512) return false
        if (value.startsWith("/") && (value.endsWith("/") || value.endsWith("/i"))) {
            return try {
                val ignoreCase = value.endsWith("/i")
                val regex = Pattern.compile(value.drop(1).dropLast(if (ignoreCase) 2 else 1), if (ignoreCase) Pattern.CASE_INSENSITIVE else 0)
                regex.matcher(sender).find() || regex.matcher(body).find()
            } catch (_: Exception) { false }
        }
        return sender.contains(value, true) || body.contains(value, true)
    }
}
