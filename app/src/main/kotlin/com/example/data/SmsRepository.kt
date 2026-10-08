package com.example.data

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest

class SmsRepository(private val smsDao: SmsDao, private val vault: LocalVault, private val settings: SettingsDataStore) {
    val allRules = smsDao.getAllRules()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recentLogs = settings.retentionDays.flatMapLatest { days -> smsDao.getRecentLogs(System.currentTimeMillis() - days * 86400000L) }.map { logs -> logs.map { vault.reveal(it) } }
    val queueCount = smsDao.queueCount()
    suspend fun insertRule(rule: ForwardingRule) = smsDao.insertRule(rule)
    suspend fun deleteRule(id: Int) = smsDao.deleteRuleById(id)
    suspend fun toggleRule(id: Int, isActive: Boolean) = smsDao.setRuleActive(id, isActive)
    suspend fun clearLogs() = smsDao.clearLogs()
    suspend fun deleteLog(id: Int) = smsDao.deleteLogById(id)
    suspend fun cancelPending() = smsDao.cancelPending()
}
