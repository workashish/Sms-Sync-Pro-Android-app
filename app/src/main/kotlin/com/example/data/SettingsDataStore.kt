package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val dao: SmsDao,
    private val vault: LocalVault
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val defaults = DefaultConfig.load(context).getJSONObject("settings").apply {
        put("captureRcs", false); put("updateUrl", "https://api.github.com/repos/workashish/Sms-Sync-Pro-Android-app/releases/latest")
        put("authorizedCommandSenders", ""); put("smsSubscriptionId", -1); put("retentionDays", 30)
    }
    private val _webhookSecretFlow = MutableStateFlow(defaults.getString("webhookSecret"))
    val webhookSecretFlow = _webhookSecretFlow.asStateFlow()
    private val _aesEncryptionKeyFlow = MutableStateFlow(defaults.getString("aesEncryptionKey"))
    val aesEncryptionKeyFlow = _aesEncryptionKeyFlow.asStateFlow()
    init { scope.launch { ensureConfig() } }
    private suspend fun ensureConfig() = mutex.withLock {
        if (dao.getConfig() == null) {
            val json = JSONObject(defaults.toString()).put("_fingerprintKey", randomFingerprintKey())
            val old = dataStore.data.first()
            val booleans = mapOf("globalEnable" to GLOBAL_ENABLE, "includeDeviceModel" to INCLUDE_DEVICE_MODEL,
                "retryFailedWebhooks" to RETRY_FAILED_WEBHOOKS, "preventScreenCapture" to PREVENT_SCREEN_CAPTURE,
                "enableSmsCommands" to ENABLE_SMS_COMMANDS, "captureRcs" to CAPTURE_RCS)
            booleans.forEach { (name, key) -> old[key]?.let { json.put(name, it) } }
            old[WEBHOOK_TIMEOUT]?.let { json.put("webhookTimeout", it.coerceIn(1, 60)) }
            old[CUSTOM_WEBHOOK_TEMPLATE]?.let { json.put("customWebhookTemplate", it) }
            old[UPDATE_URL]?.let { json.put("updateUrl", it) }
            // Legacy encrypted preferences only existed on API 23+. New storage also supports API 21/22.
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                val legacy = EncryptedSharedPreferences.create(context, "secure_settings", master,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
                legacy.getString("webhook_secret", null)?.let { json.put("webhookSecret", it) }
                legacy.getString("aes_encryption_key", null)?.let { json.put("aesEncryptionKey", it) }
                dao.saveConfig(AppConfig(payload = vault.encrypt(json.toString())))
                legacy.edit().clear().commit()
            } else dao.saveConfig(AppConfig(payload = vault.encrypt(json.toString())))
            dataStore.edit { it.clear() }
        } else {
            val existing = JSONObject(vault.decrypt(dao.getConfig()!!.payload))
            if (!existing.has("_fingerprintKey")) dao.saveConfig(AppConfig(payload = vault.encrypt(existing.put("_fingerprintKey", randomFingerprintKey()).toString())))
        }
    }
    private fun randomFingerprintKey(): String = android.util.Base64.encodeToString(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }, android.util.Base64.NO_WRAP)
    private val config = dao.observeConfig().filterNotNull().map { JSONObject(vault.decrypt(it.payload)) }.onEach {
        _webhookSecretFlow.value = it.optString("webhookSecret", defaults.getString("webhookSecret"))
        _aesEncryptionKeyFlow.value = it.optString("aesEncryptionKey", defaults.getString("aesEncryptionKey"))
    }
    suspend fun snapshot(): JSONObject { ensureConfig(); return JSONObject(vault.decrypt(dao.getConfig()!!.payload)) }
    private suspend fun set(name: String, value: Any) { ensureConfig(); mutex.withLock {
        val json = JSONObject(vault.decrypt(dao.getConfig()!!.payload)).put(name, value)
        dao.saveConfig(AppConfig(payload = vault.encrypt(json.toString())))
        if (name == "webhookSecret") _webhookSecretFlow.value = value.toString()
        if (name == "aesEncryptionKey") _aesEncryptionKeyFlow.value = value.toString()
    } }
    suspend fun applyImport(values: JSONObject, rules: List<ForwardingRule>) { ensureConfig(); mutex.withLock {
        val json = JSONObject(vault.decrypt(dao.getConfig()!!.payload))
        values.keys().forEach { json.put(it, values.get(it)) }
        dao.importAtomically(AppConfig(payload = vault.encrypt(json.toString())), rules)
        _webhookSecretFlow.value = json.getString("webhookSecret")
        _aesEncryptionKeyFlow.value = json.getString("aesEncryptionKey")
    } }
    private fun boolean(name: String): Flow<Boolean> = config.map { it.optBoolean(name, defaults.optBoolean(name)) }
    private fun string(name: String): Flow<String> = config.map { it.optString(name, defaults.optString(name)) }
    val globalEnable = boolean("globalEnable")
    val includeDeviceModel = boolean("includeDeviceModel")
    val retryFailedWebhooks = boolean("retryFailedWebhooks")
    val preventScreenCapture = boolean("preventScreenCapture")
    val enableSmsCommands = boolean("enableSmsCommands")
    val captureRcs = boolean("captureRcs")
    val webhookTimeout: Flow<Int> = config.map { it.optInt("webhookTimeout", 8).coerceIn(1, 60) }
    val customWebhookTemplate = string("customWebhookTemplate")
    val updateUrl = string("updateUrl")
    val authorizedCommandSenders = string("authorizedCommandSenders")
    val smsSubscriptionId: Flow<Int> = config.map { it.optInt("smsSubscriptionId", -1) }
    val retentionDays: Flow<Int> = config.map { it.optInt("retentionDays", 30).coerceIn(1, 365) }
    suspend fun updateGlobalEnable(value: Boolean) { set("globalEnable", value); if (value) com.example.worker.QueueScheduler.recover(context, resume = true) }
    suspend fun retryFailedWebhooksNow() { dao.retryFailedWebhooks(); com.example.worker.QueueScheduler.recover(context, resume = true) }
    suspend fun updateIncludeDeviceModel(value: Boolean) = set("includeDeviceModel", value)
    suspend fun updateWebhookTimeout(value: Int) = set("webhookTimeout", value.coerceIn(1, 60))
    suspend fun updateRetryFailedWebhooks(value: Boolean) = set("retryFailedWebhooks", value)
    suspend fun updatePreventScreenCapture(value: Boolean) = set("preventScreenCapture", value)
    suspend fun updateCustomWebhookTemplate(value: String) = set("customWebhookTemplate", value)
    suspend fun updateEnableSmsCommands(value: Boolean) = set("enableSmsCommands", value)
    suspend fun updateCaptureRcs(value: Boolean) = set("captureRcs", value)
    suspend fun updateUpdateUrl(value: String) = set("updateUrl", value)
    suspend fun updateAuthorizedCommandSenders(value: String) = set("authorizedCommandSenders", value)
    suspend fun updateSmsSubscriptionId(value: Int) = set("smsSubscriptionId", value)
    suspend fun updateRetentionDays(value: Int) = set("retentionDays", value.coerceIn(1, 365))
    suspend fun updateWebhookSecret(value: String) = set("webhookSecret", value)
    suspend fun updateAesEncryptionKey(value: String) = set("aesEncryptionKey", value)
    fun getWebhookSecret(): String = _webhookSecretFlow.value
    fun getAesEncryptionKey(): String = _aesEncryptionKeyFlow.value
    companion object {
        val GLOBAL_ENABLE = booleanPreferencesKey("global_enable")
        val INCLUDE_DEVICE_MODEL = booleanPreferencesKey("include_device_model")
        val WEBHOOK_TIMEOUT = intPreferencesKey("webhook_timeout")
        val RETRY_FAILED_WEBHOOKS = booleanPreferencesKey("retry_failed_webhooks")
        val PREVENT_SCREEN_CAPTURE = booleanPreferencesKey("prevent_screen_capture")
        val CUSTOM_WEBHOOK_TEMPLATE = stringPreferencesKey("custom_webhook_template")
        val ENABLE_SMS_COMMANDS = booleanPreferencesKey("enable_sms_commands")
        val CAPTURE_RCS = booleanPreferencesKey("capture_rcs")
        val UPDATE_URL = stringPreferencesKey("update_url")
    }
}
