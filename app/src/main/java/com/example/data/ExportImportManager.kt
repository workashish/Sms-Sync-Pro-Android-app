package com.example.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

class ExportImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore,
    private val smsDao: SmsDao
) {

    suspend fun exportConfig(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val rules = smsDao.getAllRulesNonFlow()

            val root = JSONObject()
            
            val settingsJson = JSONObject().apply {
                put("globalEnable", settings.globalEnable.first())
                put("includeDeviceModel", settings.includeDeviceModel.first())
                put("retryFailedWebhooks", settings.retryFailedWebhooks.first())
                put("webhookTimeout", settings.webhookTimeout.first())
                put("preventScreenCapture", settings.preventScreenCapture.first())
                put("customWebhookTemplate", settings.customWebhookTemplate.first())
                put("enableSmsCommands", settings.enableSmsCommands.first())
            }
            root.put("settings", settingsJson)

            val rulesArray = JSONArray()
            rules.forEach { rule ->
                val ruleJson = JSONObject().apply {
                    put("name", rule.name)
                    put("type", rule.type)
                    put("target", rule.target)
                    put("keywordFilter", rule.keywordFilter)
                    put("isActive", rule.isActive)
                }
                rulesArray.put(ruleJson)
            }
            root.put("rules", rulesArray)

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

            if (root.has("settings")) {
                val settingsJson = root.getJSONObject("settings")
                if (settingsJson.has("globalEnable")) settings.updateGlobalEnable(settingsJson.getBoolean("globalEnable"))
                if (settingsJson.has("includeDeviceModel")) settings.updateIncludeDeviceModel(settingsJson.getBoolean("includeDeviceModel"))
                if (settingsJson.has("retryFailedWebhooks")) settings.updateRetryFailedWebhooks(settingsJson.getBoolean("retryFailedWebhooks"))
                if (settingsJson.has("webhookTimeout")) settings.updateWebhookTimeout(settingsJson.getInt("webhookTimeout"))
                if (settingsJson.has("preventScreenCapture")) settings.updatePreventScreenCapture(settingsJson.getBoolean("preventScreenCapture"))
                if (settingsJson.has("customWebhookTemplate")) settings.updateCustomWebhookTemplate(settingsJson.getString("customWebhookTemplate"))
                if (settingsJson.has("enableSmsCommands")) settings.updateEnableSmsCommands(settingsJson.getBoolean("enableSmsCommands"))
            }

            if (root.has("rules")) {
                val rulesArray = root.getJSONArray("rules")
                for (i in 0 until rulesArray.length()) {
                    val r = rulesArray.getJSONObject(i)
                    if (!r.has("name") || !r.has("type") || !r.has("target")) {
                        continue // Basic schema validation
                    }
                    smsDao.insertRule(
                        ForwardingRule(
                            name = r.optString("name", "Imported Rule"),
                            type = r.optString("type", "WEBHOOK"),
                            target = r.optString("target", ""),
                            keywordFilter = r.optString("keywordFilter", ""),
                            isActive = r.optBoolean("isActive", true)
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
