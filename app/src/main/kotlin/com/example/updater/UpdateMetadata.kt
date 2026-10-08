package com.example.updater

import org.json.JSONObject
import java.net.URI

object UpdateMetadata {
    fun secureUrl(value: String): String {
        val uri = URI(value)
        require((uri.scheme == "https" || (com.example.BuildConfig.DEBUG && uri.scheme == "http" && uri.host in setOf("10.0.2.2", "127.0.0.1", "localhost"))) && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "Update URL must be a valid HTTPS URL."
        }
        return value
    }

    fun checksum(value: String): String {
        val digest = value.removePrefix("sha256:")
        require(digest.matches(Regex("[a-fA-F0-9]{64}"))) { "Update metadata must include a SHA-256 checksum." }
        return digest.lowercase()
    }
    fun parse(json: JSONObject, currentCode: Int, currentName: String): UpdateInfo? {
        if (json.has("tag_name")) {
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
            val tag = json.getString("tag_name")
            val version = Regex("^v?([0-9]+(?:\\.[0-9]+)*)(?:[-+]build[.-]?([0-9]+))?$").matchEntire(tag)
                ?: throw IllegalArgumentException("Release tag must be a version such as v2.1.0 or v2.0-build3.")
            val base = version.groupValues[1]
            val build = version.groupValues[2].toIntOrNull()
            val newer = VersionComparison.isNewer(base, currentName) ||
                (!VersionComparison.isNewer(currentName, base) && build != null && build > currentCode)
            if (!newer) return null
            val assets = json.optJSONArray("assets") ?: throw IllegalArgumentException("Release has no APK attached.")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull {
                it.optString("name").endsWith(".apk", true)
            } ?: throw IllegalArgumentException("Release has no APK attached.")
            return UpdateInfo(build ?: 0, tag, secureUrl(apk.getString("browser_download_url")), json.optString("body"), checksum(apk.optString("digest")))
        }
        require(json.has("versionCode")) { "Update metadata is missing versionCode." }
        val code = json.getInt("versionCode")
        if (code <= currentCode) return null
        return UpdateInfo(code, json.optString("versionName", "Build $code"), secureUrl(json.getString("downloadUrl")), json.optString("releaseNotes"), checksum(json.optString("sha256")))
    }
}
