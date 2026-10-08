package com.example.worker

import android.content.Context
import androidx.work.*
import com.example.data.*
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

object QueueScheduler {
    fun receipt(context: Context, id: String) {
        val request = OneTimeWorkRequestBuilder<ReceiptWorker>().setInputData(workDataOf("receiptId" to id))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniqueWork("receipt_$id", ExistingWorkPolicy.KEEP, request)
    }
    fun delivery(context: Context, value: Outbox, resume: Boolean = false) {
        val request = if (value.type == "SMS") OneTimeWorkRequestBuilder<SmsSendWorker>().setInputData(workDataOf("outboxId" to value.id)).build()
        else OneTimeWorkRequestBuilder<WebhookWorker>().setInputData(workDataOf("outboxId" to value.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniqueWork("delivery_${value.id}", if (resume) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }
    fun recover(context: Context, resume: Boolean = false) {
        WorkManager.getInstance(context).enqueueUniqueWork("recover_queue", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<QueueRecoveryWorker>().setInputData(workDataOf("resume" to resume)).build())
    }
    fun initialize(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("queue_maintenance", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<QueueRecoveryWorker>(15, TimeUnit.MINUTES).build())
        recover(context)
    }
    suspend fun test(context: Context, rule: ForwardingRule) {
        val id = UUID.randomUUID().toString()
        val payload = JSONObject().put("url", rule.target).put("sender", "+12345678900")
            .put("message", "Test message from SMS Sync Pro.").put("ruleName", "Test: ${rule.name}")
        val value = Outbox(id, id, "WEBHOOK", LocalVault(context).encrypt(payload.toString()), System.currentTimeMillis(), isTest = true)
        AppDatabase.getDatabase(context).smsDao().insertOutbox(value)
        delivery(context, value)
    }
}
