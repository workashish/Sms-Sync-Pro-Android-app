package com.example.data

class SmsRepository(private val smsDao: SmsDao) {
    val allRules = smsDao.getAllRules()
    val recentLogs = smsDao.getRecentLogs()

    suspend fun insertRule(rule: ForwardingRule) = smsDao.insertRule(rule)
    
    suspend fun deleteRule(id: Int) = smsDao.deleteRuleById(id)
    
    suspend fun toggleRule(id: Int, isActive: Boolean) = smsDao.setRuleActive(id, isActive)
    
    suspend fun clearLogs() = smsDao.clearLogs()
    
    suspend fun deleteLog(id: Int) = smsDao.deleteLogById(id)
}
