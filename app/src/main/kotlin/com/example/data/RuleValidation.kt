package com.example.data

import java.net.URI

object RuleValidation {
    fun error(type: String, target: String, filter: String, allowHttp: Boolean): String? {
        val destination = target.trim()
        if (type == "WEBHOOK") {
            val uri = try { URI(destination) } catch (_: Exception) { return "Enter a valid webhook URL." }
            if (uri.host.isNullOrBlank() || uri.userInfo != null ||
                (uri.scheme != "https" && !(allowHttp && uri.scheme == "http")))
                return if (allowHttp) "Enter a complete HTTP or HTTPS URL." else "Enter a complete HTTPS URL."
        } else if (type == "SMS") {
            if (!destination.matches(Regex("\\+?[0-9][0-9 ()-]{1,24}")) || destination.count { it.isDigit() } !in 3..15)
                return "Enter a valid phone number with 3 to 15 digits."
        } else return "Select a supported target type."
        val keyword = filter.trim()
        if (keyword.length > 512) return "Keep the filter under 512 characters."
        if (keyword.startsWith("/") && (keyword.endsWith("/") || keyword.endsWith("/i"))) {
            val pattern = keyword.drop(1).dropLast(if (keyword.endsWith("/i")) 2 else 1)
            try { com.google.re2j.Pattern.compile(pattern) } catch (_: Exception) { return "The filter contains an invalid regular expression." }
        }
        return null
    }
}
