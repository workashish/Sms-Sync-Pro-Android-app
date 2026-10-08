package com.example.worker

import org.json.JSONArray
import org.json.JSONObject

object WebhookPayload {
    fun renderTemplate(template: String, sender: String, message: String, deviceModel: String, messageId: String = "", timestamp: Long = 0, encryption: String = "none"): String {
        val values = mapOf("sender" to sender, "message" to message, "body" to message, "device_model" to deviceModel, "id" to messageId, "timestamp" to timestamp.toString(), "encryption" to encryption)
        fun render(value: Any?): Any? = when (value) {
            is JSONObject -> value.apply {
                keys().asSequence().toList().forEach { key -> put(key, render(get(key))) }
            }
            is JSONArray -> value.apply {
                for (index in 0 until length()) put(index, render(get(index)))
            }
            is String -> if (value == "{timestamp}") timestamp else Regex("\\{(sender|message|body|device_model|id|timestamp|encryption)\\}").replace(value) { match ->
                values.getValue(match.groupValues[1])
            }
            else -> value
        }
        return render(JSONObject(template)).toString()
    }
}
