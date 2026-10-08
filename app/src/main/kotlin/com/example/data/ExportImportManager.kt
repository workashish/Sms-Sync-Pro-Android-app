package com.example.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class ExportImportManager @Inject constructor(@ApplicationContext private val context: Context,
    private val settings: SettingsDataStore, private val dao: SmsDao) {
    suspend fun exportConfig(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val configuration = settings.snapshot().apply { FixedSettings.keys.forEach { remove(it) }; remove("_fingerprintKey") }
            val rules = JSONArray()
            dao.getAllRulesNonFlow().forEach { rule -> rules.put(JSONObject().put("name", rule.name).put("type", rule.type)
                .put("target", rule.target).put("keywordFilter", rule.keywordFilter).put("isActive", rule.isActive)) }
            val root = JSONObject().put("schema_version", 1).put("secretsIncluded", false).put("settings", configuration).put("rules", rules)
            val output = context.contentResolver.openOutputStream(uri) ?: return@withContext false
            output.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString(2)) }
            true
        } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
    }
    suspend fun importConfig(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext false
            val bytes = input.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val size = stream.read(buffer)
                    if (size < 0) break
                    require(out.size() + size <= 1024 * 1024) { "Configuration too large." }
                    out.write(buffer, 0, size)
                }
                out.toByteArray()
            }
            val validated = ConfigImport.parse(bytes.toString(Charsets.UTF_8), com.example.BuildConfig.DEBUG)
            settings.applyImport(validated.settings, validated.rules)
            com.example.worker.QueueScheduler.recover(context, resume = true)
            true
        } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
    }
}
