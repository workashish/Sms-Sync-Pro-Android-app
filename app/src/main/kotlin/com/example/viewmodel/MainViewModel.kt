package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.example.data.ForwardingRule
import com.example.data.SettingsDataStore
import com.example.data.SmsRepository
import com.example.data.ExportImportManager
import com.example.updater.AppUpdater
import com.example.updater.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: SmsRepository,
    val settings: SettingsDataStore,
    private val exportImportManager: ExportImportManager,
    private val appUpdater: AppUpdater
) : ViewModel() {

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate = _isCheckingUpdate.asStateFlow()

    private val _updateStatus = MutableStateFlow<String?>(null)
    val updateStatus = _updateStatus.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    fun showNotice(message: String) { _notice.value = message }
    fun clearNotice() { _notice.value = null }

    fun checkForUpdate() {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateStatus.value = null
            _updateInfo.value = null
            try {
                _updateInfo.value = appUpdater.checkForUpdate()
                _updateStatus.value = if (_updateInfo.value == null) "No newer update is available." else "Update available. Download it below."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _updateStatus.value = e.message ?: "Unable to check for updates. Please retry." }
            finally { _isCheckingUpdate.value = false }
        }
    }

    fun downloadUpdate(url: String, version: String, sha256: String) {
        try {
            appUpdater.downloadUpdate(url, version, sha256)
            _updateInfo.value = null
            _updateStatus.value = "Update download started. Return to Settings when it finishes."
        } catch (e: Exception) { _updateStatus.value = e.message ?: "Unable to start download. Please retry." }
    }

    val rules = repository.allRules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val queueCount = repository.queueCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    fun cancelPending() = viewModelScope.launch { repository.cancelPending(); _notice.value = "Unsent queued deliveries cancelled. In-flight deliveries may still finish." }
    fun retryFailedWebhooksNow() = viewModelScope.launch { settings.retryFailedWebhooksNow(); _notice.value = "Failed webhooks queued with their original IDs." }
    fun updateAuthorizedCommandSenders(value: String) = viewModelScope.launch { settings.updateAuthorizedCommandSenders(value) }
    fun updateSmsSubscriptionId(value: Int) = viewModelScope.launch { settings.updateSmsSubscriptionId(value) }
    fun updateRetentionDays(value: Int) = viewModelScope.launch { settings.updateRetentionDays(value) }
    fun editRule(rule: ForwardingRule) = viewModelScope.launch { repository.insertRule(rule) }

    val logs = repository.recentLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addRule(name: String, type: String, target: String, keywordFilter: String) {
        viewModelScope.launch {
            repository.insertRule(
                ForwardingRule(
                    name = name,
                    type = type,
                    target = target,
                    keywordFilter = keywordFilter
                )
            )
        }
    }

    fun deleteRule(id: Int) {
        viewModelScope.launch {
            repository.deleteRule(id)
        }
    }

    fun toggleRule(id: Int, isActive: Boolean) {
        viewModelScope.launch {
            repository.toggleRule(id, isActive)
        }
    }
    
    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun deleteLog(id: Int) {
        viewModelScope.launch {
            repository.deleteLog(id)
        }
    }
    
    fun updateGlobalEnable(value: Boolean) = viewModelScope.launch { settings.updateGlobalEnable(value) }
    fun updateIncludeDeviceModel(value: Boolean) = viewModelScope.launch { settings.updateIncludeDeviceModel(value) }
    fun updateWebhookTimeout(value: Int) = viewModelScope.launch { settings.updateWebhookTimeout(value) }
    fun updateRetryFailedWebhooks(value: Boolean) = viewModelScope.launch { settings.updateRetryFailedWebhooks(value) }
    fun updatePreventScreenCapture(value: Boolean) = viewModelScope.launch { settings.updatePreventScreenCapture(value) }
    fun updateCustomWebhookTemplate(value: String) = viewModelScope.launch { settings.updateCustomWebhookTemplate(value) }
    fun updateEnableSmsCommands(value: Boolean) = viewModelScope.launch { settings.updateEnableSmsCommands(value) }
    fun updateCaptureRcs(value: Boolean) = viewModelScope.launch { settings.updateCaptureRcs(value) }
    fun updateUpdateUrl(value: String) = viewModelScope.launch { settings.updateUpdateUrl(value) }
    fun updateWebhookSecret(value: String) = viewModelScope.launch { settings.updateWebhookSecret(value) }
    fun updateAesEncryptionKey(value: String) = viewModelScope.launch { settings.updateAesEncryptionKey(value) }
    
    fun exportConfig(uri: Uri) {
        viewModelScope.launch {
            _notice.value = if (exportImportManager.exportConfig(uri)) "Configuration exported." else "Export failed. Please choose another file location."
        }
    }
    
    fun importConfig(uri: Uri) {
        viewModelScope.launch {
            _notice.value = if (exportImportManager.importConfig(uri)) "Configuration imported." else "Import failed. Please check the configuration file."
        }
    }
}
