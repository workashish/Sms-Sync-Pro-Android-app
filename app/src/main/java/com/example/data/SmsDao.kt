package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    // Rules
    @Query("SELECT * FROM forwarding_rules")
    fun getAllRules(): Flow<List<ForwardingRule>>
    
    @Query("SELECT * FROM forwarding_rules WHERE isActive = 1")
    suspend fun getActiveRules(): List<ForwardingRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: ForwardingRule)

    @Query("DELETE FROM forwarding_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Int)
    
    @Query("UPDATE forwarding_rules SET isActive = :isActive WHERE id = :id")
    suspend fun setRuleActive(id: Int, isActive: Boolean)

    // Logs
    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<SmsLog>>

    @Insert
    suspend fun insertLog(log: SmsLog)
    
    @Query("DELETE FROM sms_logs WHERE id = :id")
    suspend fun deleteLogById(id: Int)

    @Query("DELETE FROM sms_logs")
    suspend fun clearLogs()
}
