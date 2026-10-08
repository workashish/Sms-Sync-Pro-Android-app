package com.example.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.data.*
import com.example.receiver.SmsStatusReceiver
import dagger.assisted.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject

@HiltWorker
class SmsSendWorker @AssistedInject constructor(@Assisted context: Context, @Assisted params: WorkerParameters,
    private val dao: SmsDao, private val settings: SettingsDataStore, private val vault: LocalVault) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val value = inputData.getString("outboxId")?.let { dao.outbox(it) } ?: return Result.success()
        if (value.status != "PENDING") return Result.success()
        if (!settings.globalEnable.first()) return Result.retry()
        val payload = JSONObject(vault.decrypt(value.payload))
        val target = payload.getString("url")
        val sender = payload.getString("sender")
        val rule = payload.getString("ruleName")
        val body = payload.getString("message")
        try {
            check(ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) { "Grant Send SMS permission in Settings." }
            val telephony = applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            check(telephony.simState == TelephonyManager.SIM_STATE_READY) { "No ready SIM. Insert/unlock a SIM and retry." }
            val selected = settings.smsSubscriptionId.first()
            val subscription = if (selected >= 0) selected else if (Build.VERSION.SDK_INT >= 24) SmsManager.getDefaultSmsSubscriptionId() else -1
            if (Build.VERSION.SDK_INT >= 24) check(subscription >= 0) { "Choose a SIM in Settings or set Android's default SMS SIM." }
            @Suppress("DEPRECATION")
            val manager = if (Build.VERSION.SDK_INT >= 22 && subscription >= 0) SmsManager.getSmsManagerForSubscriptionId(subscription) else SmsManager.getDefault()
            val text = if (rule == "Authorized STATUS") body else "$LOOP_MARKER\n$sender:\n$body"
            val parts = manager.divideMessage(text)
            dao.insertSmsParts(parts.indices.map { SmsPart(value.id, it, parts.size) })
            // Once handed to the carrier, an uncertain result is never auto-resubmitted.
            if (dao.claimSms(value.id, System.currentTimeMillis()) != 1) return Result.success()
            val sent = ArrayList(parts.indices.map { callback(value.id, it, "SENT") })
            val delivered = ArrayList(parts.indices.map { callback(value.id, it, "DELIVERED") })
            manager.sendMultipartTextMessage(target, null, parts, sent, delivered)
            dao.insertLog(vault.protect(SmsLog(sender = sender, message = body, ruleName = rule, target = target, status = "SENDING")))
            return Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            dao.outboxStatus(value.id, "FAILED")
            dao.insertLog(vault.protect(SmsLog(sender = sender, message = body, ruleName = rule, target = target, status = "FAILED: ${e.message ?: "SMS send failed"}")))
            return Result.failure()
        }
    }
    private fun callback(id: String, part: Int, event: String): PendingIntent {
        val intent = Intent(applicationContext, SmsStatusReceiver::class.java).apply {
            action = "${applicationContext.packageName}.SMS_$event"
            data = Uri.parse("smssync://callback/$id/$part/$event")
            putExtra("outboxId", id); putExtra("part", part); putExtra("event", event)
        }
        return PendingIntent.getBroadcast(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    companion object { const val LOOP_MARKER = "[SMS Sync Pro forwarded]" }
}
