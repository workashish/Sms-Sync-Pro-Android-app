package com.example.processor

import android.content.Context
import androidx.room.withTransaction
import com.example.data.*
import com.example.worker.QueueScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore,
    private val dao: SmsDao,
    private val db: AppDatabase,
    private val vault: LocalVault
) {
    suspend fun processMessage(sender: String, messageBody: String, timestamp: Long = System.currentTimeMillis(), source: String = "SMS", eventId: String? = null): String? {
        if (!settings.globalEnable.first() || sender.isBlank() || messageBody.isBlank() || messageBody.startsWith(com.example.worker.SmsSendWorker.LOOP_MARKER)) return null
        if (messageBody.toByteArray(Charsets.UTF_8).size > 262144) {
            dao.insertLog(vault.protect(SmsLog(sender = sender, message = "[Oversized message body omitted]", ruleName = "Capture", target = "", status = "FAILED: text exceeds the supported 256 KB size")))
            return null
        }
        val hash = fingerprint(messageBody)
        val event = fingerprint(eventId ?: "$source|$sender|$messageBody|$timestamp")
        val id = UUID.nameUUIDFromBytes(event.toByteArray(Charsets.UTF_8)).toString()
        val inserted = db.withTransaction {
            // Cross-source equality only; repeated real SMS have independent event IDs.
            if (dao.crossSourceDuplicate(hash, source, timestamp - 90000, timestamp + 90000) > 0) false
            else dao.insertReceipt(Receipt(id, event, hash, source, timestamp,
                vault.encrypt(JSONObject().put("sender", sender).put("message", messageBody).toString()))) != -1L
        }
        if (inserted) QueueScheduler.receipt(context, id)
        return dao.receipt(id)?.id
    }
    suspend fun route(receipt: Receipt) {
        val payload = JSONObject(vault.decrypt(receipt.payload))
        val sender = payload.getString("sender")
        val message = payload.getString("message")
        val authorized = settings.authorizedCommandSenders.first().split(',', '\n', ';').map { normalizePhone(it) }.filter { it.isNotEmpty() }
        val command = message.trim().uppercase(java.util.Locale.ROOT)
        val commandsEnabled = settings.enableSmsCommands.first()
        db.withTransaction {
        if (dao.receipt(receipt.id)?.processed != false) return@withTransaction
        if (commandsEnabled && normalizePhone(sender) in authorized && command == "STATUS") {
            val battery = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percentage = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "Unknown"
            val response = "SMS Sync Pro STATUS\nBattery: $percentage\nPending deliveries: ${dao.pendingOutbox().size}"
            enqueue(receipt, -1, "SMS", sender, response, "Authorized STATUS", sender)
        } else {
            dao.getActiveRules().forEach { rule ->
                val error = RuleValidation.error(rule.type, rule.target, rule.keywordFilter, com.example.BuildConfig.DEBUG)
                if (error != null) {
                    dao.insertLog(vault.protect(SmsLog(sender = sender, message = message, ruleName = rule.name, target = rule.target, status = "FAILED: $error")))
                    return@forEach
                }
                if (SafeFilter.matches(rule.keywordFilter, sender, message))
                    enqueue(receipt, rule.id, rule.type, sender, message, rule.name, rule.target)
            }
        }
        dao.markProcessed(receipt.id)
        }
        dao.pendingForReceipt(receipt.id).forEach { QueueScheduler.delivery(context, it) }
    }
    private suspend fun enqueue(receipt: Receipt, ruleId: Int, type: String, sender: String, body: String, ruleName: String, target: String) {
        val id = UUID.nameUUIDFromBytes("${receipt.id}|$ruleId|$type".toByteArray(Charsets.UTF_8)).toString()
        val json = JSONObject().put("sender", sender).put("message", body).put("url", target).put("ruleName", ruleName)
        dao.insertOutbox(Outbox(id, receipt.id, type, vault.encrypt(json.toString()), receipt.receivedAt))
    }
    suspend fun fingerprint(value: String): String {
        val key = android.util.Base64.decode(settings.snapshot().getString("_fingerprintKey"), android.util.Base64.NO_WRAP)
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    companion object {
        fun normalizePhone(value: String) = value.filter { it.isDigit() }.takeIf { it.length in 3..15 } ?: ""
        fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
