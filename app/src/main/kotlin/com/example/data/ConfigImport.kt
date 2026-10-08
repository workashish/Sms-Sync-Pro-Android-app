package com.example.data

import org.json.JSONObject

object ConfigImport {
    data class Validated(val settings: JSONObject, val rules: List<ForwardingRule>)
    fun parse(text: String, allowHttp: Boolean): Validated {
        require(text.toByteArray(Charsets.UTF_8).size <= 1024 * 1024) { "Configuration exceeds 1 MB." }
        val root = JSONObject(text)
        require(root.optInt("schema_version", 1) == 1) { "Unsupported configuration version." }
        require(root.has("settings") || root.has("rules")) { "No settings or rules found." }
        val settings = if (root.has("settings")) root.getJSONObject("settings") else JSONObject()
        val booleans = setOf("globalEnable", "includeDeviceModel", "retryFailedWebhooks", "preventScreenCapture", "enableSmsCommands", "captureRcs")
        val strings = setOf("webhookSecret", "aesEncryptionKey", "customWebhookTemplate", "updateUrl", "authorizedCommandSenders")
        settings.keys().forEach { key ->
            val value = settings.get(key)
            when (key) {
                in booleans -> require(value is Boolean) { "$key must be true or false." }
                in strings -> require(value is String && value.length <= 65536) { "Invalid $key." }
                "webhookTimeout", "retentionDays", "smsSubscriptionId" -> {
                    require(value is Number && value.toDouble() == value.toInt().toDouble()) { "$key must be an integer." }
                    val range = when (key) { "webhookTimeout" -> 1..60; "retentionDays" -> 1..365; else -> -1..Int.MAX_VALUE }
                    require(value.toInt() in range) { "$key is outside the supported range." }
                }
                else -> throw IllegalArgumentException("Unknown setting: $key")
            }
        }
        if (settings.optString("updateUrl").isNotBlank()) com.example.updater.UpdateMetadata.secureUrl(settings.getString("updateUrl"))
        if (settings.optString("customWebhookTemplate").isNotBlank()) JSONObject(settings.getString("customWebhookTemplate"))
        val rules = if (!root.has("rules")) emptyList() else {
            val array = root.getJSONArray("rules")
            require(array.length() <= 500) { "Too many rules." }
            (0 until array.length()).map {
                val value = array.getJSONObject(it)
                val name = value.getString("name").trim()
                val type = value.getString("type")
                val target = value.getString("target").trim()
                val filter = value.optString("keywordFilter").trim()
                require(name.isNotBlank() && name.length <= 128 && filter.length <= 512) { "Invalid rule name or filter." }
                RuleValidation.error(type, target, filter, allowHttp)?.let { error -> throw IllegalArgumentException(error) }
                if (value.has("isActive")) require(value.get("isActive") is Boolean) { "isActive must be true or false." }
                ForwardingRule(name = name, type = type, target = target, keywordFilter = filter, isActive = value.optBoolean("isActive", true))
            }
        }
        return Validated(settings, rules)
    }
}
