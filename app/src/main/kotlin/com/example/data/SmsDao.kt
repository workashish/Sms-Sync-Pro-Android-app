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

    @Query("SELECT * FROM forwarding_rules")
    suspend fun getAllRulesNonFlow(): List<ForwardingRule>
    
    @Query("SELECT * FROM forwarding_rules WHERE isActive = 1")
    suspend fun getActiveRules(): List<ForwardingRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: ForwardingRule)

    @Query("DELETE FROM forwarding_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Int)
    
    @Query("UPDATE forwarding_rules SET isActive = :isActive WHERE id = :id")
    suspend fun setRuleActive(id: Int, isActive: Boolean)

    // Logs
    @Query("SELECT * FROM sms_logs WHERE timestamp >= :cutoff ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(cutoff: Long = 0): Flow<List<SmsLog>>

    @Insert
    suspend fun insertLog(log: SmsLog)
    
    @Query("DELETE FROM sms_logs WHERE id = :id")
    suspend fun deleteLogById(id: Int)

    @Query("DELETE FROM sms_logs")
    suspend fun clearLogs()

    @Query("SELECT * FROM app_config WHERE id = 1") fun observeConfig(): Flow<AppConfig?>
    @Query("SELECT * FROM app_config WHERE id = 1") suspend fun getConfig(): AppConfig?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveConfig(config: AppConfig)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertReceipt(receipt: Receipt): Long
    @Query("SELECT * FROM receipts WHERE id = :id") suspend fun receipt(id: String): Receipt?
    @Query("SELECT * FROM receipts WHERE processed = 0 LIMIT 100") suspend fun pendingReceipts(): List<Receipt>
    @Query("SELECT COUNT(*) FROM receipts WHERE bodyHash = :hash AND source != :source AND receivedAt BETWEEN :start AND :end")
    suspend fun crossSourceDuplicate(hash: String, source: String, start: Long, end: Long): Int
    @Query("UPDATE receipts SET processed = 1 WHERE id = :id") suspend fun markProcessed(id: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertOutbox(value: Outbox): Long
    @Query("SELECT * FROM outbox WHERE id = :id") suspend fun outbox(id: String): Outbox?
    @Query("SELECT * FROM outbox WHERE status = 'PENDING'") suspend fun pendingOutbox(): List<Outbox>
    @Query("SELECT (SELECT COUNT(*) FROM outbox WHERE status IN ('PENDING', 'SENDING')) + (SELECT COUNT(*) FROM receipts WHERE processed = 0)") fun queueCount(): Flow<Int>
    @Query("UPDATE outbox SET status = :status WHERE id = :id") suspend fun outboxStatus(id: String, status: String)
    @Query("UPDATE outbox SET status = 'CANCELLED' WHERE status = 'PENDING'") suspend fun cancelPendingOutbox()
    @Query("UPDATE receipts SET processed = 1 WHERE processed = 0") suspend fun cancelReceipts()
    @androidx.room.Transaction suspend fun cancelPending() { cancelPendingOutbox(); cancelReceipts() }
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSmsParts(parts: List<SmsPart>)
    @Query("UPDATE sms_parts SET sent = :status WHERE outboxId = :id AND part = :part") suspend fun setSmsSent(id: String, part: Int, status: Int)
    @Query("UPDATE sms_parts SET delivered = :status WHERE outboxId = :id AND part = :part") suspend fun setSmsDelivered(id: String, part: Int, status: Int)
    @Query("SELECT * FROM sms_parts WHERE outboxId = :id") suspend fun smsParts(id: String): List<SmsPart>
    @Query("DELETE FROM sms_logs WHERE timestamp < :cutoff OR id NOT IN (SELECT id FROM sms_logs ORDER BY timestamp DESC LIMIT 1000)") suspend fun pruneLogs(cutoff: Long)
    @Query("DELETE FROM receipts WHERE processed = 1 AND receivedAt < :cutoff") suspend fun pruneReceipts(cutoff: Long)
    @Query("DELETE FROM outbox WHERE status NOT IN ('PENDING', 'SENDING') AND timestamp < :cutoff") suspend fun pruneOutbox(cutoff: Long)
    @Query("DELETE FROM sms_parts WHERE outboxId NOT IN (SELECT id FROM outbox)") suspend fun pruneSmsParts()
    @androidx.room.Transaction
    suspend fun mergeRules(rules: List<ForwardingRule>) {
        val existing = getAllRulesNonFlow()
        rules.distinctBy { listOf(it.name, it.type, it.target, it.keywordFilter) }.forEach { rule ->
            val match = existing.firstOrNull { it.name == rule.name && it.type == rule.type && it.target == rule.target && it.keywordFilter == rule.keywordFilter }
            insertRule(if (match == null) rule else rule.copy(id = match.id))
        }
    }
    @androidx.room.Transaction
    suspend fun importAtomically(config: AppConfig, rules: List<ForwardingRule>) {
        mergeRules(rules)
        saveConfig(config)
    }
    @Query("UPDATE outbox SET attempts = attempts + 1, attemptedAt = :now WHERE id = :id") suspend fun attempt(id: String, now: Long)
    @Query("UPDATE outbox SET status = 'SENDING', attemptedAt = :now WHERE id = :id AND status = 'PENDING'") suspend fun claimSms(id: String, now: Long): Int
    @Query("SELECT * FROM outbox WHERE status = 'SENDING' AND attemptedAt < :cutoff") suspend fun uncertainSms(cutoff: Long): List<Outbox>
    @Query("SELECT * FROM outbox WHERE receiptId = :id AND status = 'PENDING'") suspend fun pendingForReceipt(id: String): List<Outbox>
    @Query("SELECT * FROM outbox WHERE status = 'PENDING' AND id > :after ORDER BY id LIMIT 100") suspend fun pendingOutboxPage(after: String): List<Outbox>
    @Query("SELECT * FROM receipts WHERE processed = 0 AND id > :after ORDER BY id LIMIT 100") suspend fun pendingReceiptPage(after: String): List<Receipt>
    @Query("UPDATE outbox SET status = 'PENDING', attempts = 0 WHERE type = 'WEBHOOK' AND status = 'FAILED'") suspend fun retryFailedWebhooks()
}
