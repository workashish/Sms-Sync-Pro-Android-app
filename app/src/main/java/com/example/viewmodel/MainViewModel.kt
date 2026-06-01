package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ForwardingRule
import com.example.data.SettingsManager
import com.example.data.SmsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SmsRepository(AppDatabase.getDatabase(application).smsDao())
    val settingsManager = SettingsManager(application)

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
}
