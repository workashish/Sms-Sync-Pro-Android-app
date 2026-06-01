package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sms_sync_prefs", Context.MODE_PRIVATE)

    var globalEnable: Boolean
        get() = prefs.getBoolean("global_enable", true)
        set(value) = prefs.edit().putBoolean("global_enable", value).apply()

    var includeDeviceModel: Boolean
        get() = prefs.getBoolean("include_device_model", true)
        set(value) = prefs.edit().putBoolean("include_device_model", value).apply()

    var webhookTimeout: Int
        get() = prefs.getInt("webhook_timeout", 8) // in seconds
        set(value) = prefs.edit().putInt("webhook_timeout", value).apply()

    var retryFailedWebhooks: Boolean
        get() = prefs.getBoolean("retry_failed_webhooks", false)
        set(value) = prefs.edit().putBoolean("retry_failed_webhooks", value).apply()

    var webhookSecret: String
        get() = prefs.getString("webhook_secret", "") ?: ""
        set(value) = prefs.edit().putString("webhook_secret", value).apply()

    var preventScreenCapture: Boolean
        get() = prefs.getBoolean("prevent_screen_capture", false)
        set(value) = prefs.edit().putBoolean("prevent_screen_capture", value).apply()
}
