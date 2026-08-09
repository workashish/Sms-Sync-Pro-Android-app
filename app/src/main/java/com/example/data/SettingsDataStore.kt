package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _webhookSecretFlow = MutableStateFlow(securePrefs.getString("webhook_secret", null) ?: "YOUR_HMAC_SECRET_KEY")
    val webhookSecretFlow = _webhookSecretFlow.asStateFlow()

    private val _aesEncryptionKeyFlow = MutableStateFlow(securePrefs.getString("aes_encryption_key", null) ?: "YOUR_AES_PASSWORD")
    val aesEncryptionKeyFlow = _aesEncryptionKeyFlow.asStateFlow()

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

    val globalEnable: Flow<Boolean> = dataStore.data.map { it[GLOBAL_ENABLE] ?: true }
    val includeDeviceModel: Flow<Boolean> = dataStore.data.map { it[INCLUDE_DEVICE_MODEL] ?: true }
    val webhookTimeout: Flow<Int> = dataStore.data.map { it[WEBHOOK_TIMEOUT] ?: 8 }
    val retryFailedWebhooks: Flow<Boolean> = dataStore.data.map { it[RETRY_FAILED_WEBHOOKS] ?: false }
    val preventScreenCapture: Flow<Boolean> = dataStore.data.map { it[PREVENT_SCREEN_CAPTURE] ?: false }
    val customWebhookTemplate: Flow<String> = dataStore.data.map { it[CUSTOM_WEBHOOK_TEMPLATE] ?: "" }
    val enableSmsCommands: Flow<Boolean> = dataStore.data.map { it[ENABLE_SMS_COMMANDS] ?: false }
    val captureRcs: Flow<Boolean> = dataStore.data.map { it[CAPTURE_RCS] ?: false }
    val updateUrl: Flow<String> = dataStore.data.map { it[UPDATE_URL] ?: "https://api.github.com/repos/workashish/Sms-Sync-Pro-Android-app/releases/latest" }

    suspend fun updateGlobalEnable(value: Boolean) {
        dataStore.edit { it[GLOBAL_ENABLE] = value }
    }

    suspend fun updateIncludeDeviceModel(value: Boolean) {
        dataStore.edit { it[INCLUDE_DEVICE_MODEL] = value }
    }

    suspend fun updateWebhookTimeout(value: Int) {
        dataStore.edit { it[WEBHOOK_TIMEOUT] = value }
    }

    suspend fun updateRetryFailedWebhooks(value: Boolean) {
        dataStore.edit { it[RETRY_FAILED_WEBHOOKS] = value }
    }

    suspend fun updatePreventScreenCapture(value: Boolean) {
        dataStore.edit { it[PREVENT_SCREEN_CAPTURE] = value }
    }

    suspend fun updateCustomWebhookTemplate(value: String) {
        dataStore.edit { it[CUSTOM_WEBHOOK_TEMPLATE] = value }
    }

    suspend fun updateEnableSmsCommands(value: Boolean) {
        dataStore.edit { it[ENABLE_SMS_COMMANDS] = value }
    }

    suspend fun updateCaptureRcs(value: Boolean) {
        dataStore.edit { it[CAPTURE_RCS] = value }
    }

    suspend fun updateUpdateUrl(value: String) {
        dataStore.edit { it[UPDATE_URL] = value }
    }

    fun updateWebhookSecret(value: String) {
        securePrefs.edit().putString("webhook_secret", value).apply()
        _webhookSecretFlow.value = value
    }

    fun updateAesEncryptionKey(value: String) {
        securePrefs.edit().putString("aes_encryption_key", value).apply()
        _aesEncryptionKeyFlow.value = value
    }

    fun getWebhookSecret(): String = securePrefs.getString("webhook_secret", null) ?: "YOUR_HMAC_SECRET_KEY"
    fun getAesEncryptionKey(): String = securePrefs.getString("aes_encryption_key", null) ?: "YOUR_AES_PASSWORD"
}
