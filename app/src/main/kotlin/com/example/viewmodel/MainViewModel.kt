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

    fun checkForUpdate() {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateInfo.value = appUpdater.checkForUpdate()
            _isCheckingUpdate.value = false
        }
    }

    fun downloadUpdate(url: String, version: String) {
        appUpdater.downloadUpdate(url, version)
        _updateInfo.value = null // clear after starting download
    }

    val rules = repository.allRules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

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
    fun updateWebhookSecret(value: String) = settings.updateWebhookSecret(value)
    fun updateAesEncryptionKey(value: String) = settings.updateAesEncryptionKey(value)
    
    fun exportConfig(uri: Uri) {
        viewModelScope.launch {
            exportImportManager.exportConfig(uri)
        }
    }
    
    fun importConfig(uri: Uri) {
        viewModelScope.launch {
            exportImportManager.importConfig(uri)
        }
    }
}
