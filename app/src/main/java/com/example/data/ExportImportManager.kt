package com.example.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExportImportManager(private val context: Context) {

    suspend fun exportConfig(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val settings = SettingsManager(context)
            val db = AppDatabase.getDatabase(context)
            val rules = db.smsDao().getActiveRules()

            val root = JSONObject()
            
            // Settings
            val settingsJson = JSONObject().apply {
                put("globalEnable", settings.globalEnable)
                put("includeDeviceModel", settings.includeDeviceModel)
                put("retryFailedWebhooks", settings.retryFailedWebhooks)
                put("webhookTimeout", settings.webhookTimeout)
                put("webhookSecret", settings.webhookSecret)
                put("preventScreenCapture", settings.preventScreenCapture)
                put("aesEncryptionKey", settings.aesEncryptionKey)
                put("customWebhookTemplate", settings.customWebhookTemplate)
                put("enableSmsCommands", settings.enableSmsCommands)
            }
            root.put("settings", settingsJson)

            // Rules
            val rulesArray = JSONArray()
            rules.forEach { rule ->
                val ruleJson = JSONObject().apply {
                    put("name", rule.name)
                    put("type", rule.type)
                    put("target", rule.target)
                    put("keywordFilter", rule.keywordFilter)
                }
                rulesArray.put(ruleJson)
            }
            root.put("rules", rulesArray)

            // Write to file
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(root.toString(4))
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importConfig(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: return@withContext false

            val root = JSONObject(jsonString)

            // Settings
            if (root.has("settings")) {
                val settingsJson = root.getJSONObject("settings")
                val settings = SettingsManager(context)
                if (settingsJson.has("globalEnable")) settings.globalEnable = settingsJson.getBoolean("globalEnable")
                if (settingsJson.has("includeDeviceModel")) settings.includeDeviceModel = settingsJson.getBoolean("includeDeviceModel")
                if (settingsJson.has("retryFailedWebhooks")) settings.retryFailedWebhooks = settingsJson.getBoolean("retryFailedWebhooks")
                if (settingsJson.has("webhookTimeout")) settings.webhookTimeout = settingsJson.getInt("webhookTimeout")
                if (settingsJson.has("webhookSecret")) settings.webhookSecret = settingsJson.getString("webhookSecret")
                if (settingsJson.has("preventScreenCapture")) settings.preventScreenCapture = settingsJson.getBoolean("preventScreenCapture")
                if (settingsJson.has("aesEncryptionKey")) settings.aesEncryptionKey = settingsJson.getString("aesEncryptionKey")
                if (settingsJson.has("customWebhookTemplate")) settings.customWebhookTemplate = settingsJson.getString("customWebhookTemplate")
                if (settingsJson.has("enableSmsCommands")) settings.enableSmsCommands = settingsJson.getBoolean("enableSmsCommands")
            }

            // Rules
            if (root.has("rules")) {
                val db = AppDatabase.getDatabase(context)
                // We keep existing rules and just add new ones, or delete existing?
                // The prompt says "instantly restored", so we probably should add them.
                // It's safer to avoid clearing. Let's just insert them.
                val rulesArray = root.getJSONArray("rules")
                for (i in 0 until rulesArray.length()) {
                    val r = rulesArray.getJSONObject(i)
                    db.smsDao().insertRule(
                        ForwardingRule(
                            name = r.optString("name", "Imported Rule"),
                            type = r.optString("type", "WEBHOOK"),
                            target = r.optString("target", ""),
                            keywordFilter = r.optString("keywordFilter", "")
                        )
                    )
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
