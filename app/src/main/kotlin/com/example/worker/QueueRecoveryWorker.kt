package com.example.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.data.*
import dagger.assisted.*
import kotlinx.coroutines.flow.first

@HiltWorker
class QueueRecoveryWorker @AssistedInject constructor(@Assisted context: Context, @Assisted params: WorkerParameters,
    private val dao: SmsDao, private val settings: SettingsDataStore, private val vault: LocalVault) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val days = settings.retentionDays.first()
        val now = System.currentTimeMillis()
        dao.uncertainSms(now - 10 * 60 * 1000L).forEach { item ->
            dao.outboxStatus(item.id, "UNKNOWN")
            val json = org.json.JSONObject(vault.decrypt(item.payload))
            dao.insertLog(vault.protect(SmsLog(sender = json.getString("sender"), message = json.getString("message"),
                ruleName = json.getString("ruleName"), target = json.getString("url"), status = "UNKNOWN: no carrier result; not resent to prevent duplicates")))
        }
        val cutoff = now - days * 86400000L
        dao.pruneLogs(cutoff); dao.pruneReceipts(now - 2 * 86400000L); dao.pruneOutbox(cutoff); dao.pruneSmsParts()
        if (settings.globalEnable.first()) {
            var receiptCursor = ""
            while (true) {
                val page = dao.pendingReceiptPage(receiptCursor)
                if (page.isEmpty()) break
                page.forEach { QueueScheduler.receipt(applicationContext, it.id) }
                receiptCursor = page.last().id
            }
            var deliveryCursor = ""
            while (true) {
                val page = dao.pendingOutboxPage(deliveryCursor)
                if (page.isEmpty()) break
                page.forEach { QueueScheduler.delivery(applicationContext, it, inputData.getBoolean("resume", false)) }
                deliveryCursor = page.last().id
            }
        }
        WorkManager.getInstance(applicationContext).pruneWork()
        return Result.success()
    }
}
