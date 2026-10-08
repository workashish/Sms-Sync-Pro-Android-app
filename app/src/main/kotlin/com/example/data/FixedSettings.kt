package com.example.data

import org.json.JSONObject

/** These settings belong to the APK, never to imported or persisted user preferences. */
object FixedSettings {
    const val UPDATE_URL = "https://api.github.com/repos/workashish/Sms-Sync-Pro-Android-app/releases/latest"
    val keys = setOf("updateUrl", "webhookSecret", "aesEncryptionKey")

    fun applyTo(config: JSONObject, defaults: JSONObject): JSONObject = config.apply {
        put("updateUrl", UPDATE_URL)
        put("webhookSecret", defaults.getString("webhookSecret"))
        put("aesEncryptionKey", defaults.getString("aesEncryptionKey"))
    }
}
