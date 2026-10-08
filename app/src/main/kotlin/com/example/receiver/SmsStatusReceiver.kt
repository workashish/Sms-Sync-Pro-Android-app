package com.example.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import com.example.data.*
import kotlinx.coroutines.*
import org.json.JSONObject

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("outboxId") ?: return
        val part = intent.getIntExtra("part", -1)
        val event = intent.getStringExtra("event") ?: return
        val result = resultCode
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.smsDao()
                val vault = LocalVault(context)
                db.withTransaction {
                    val item = dao.outbox(id) ?: return@withTransaction
                    if (item.status in listOf("FAILED", "CANCELLED", "DELIVERED")) return@withTransaction
                    if (event == "SENT") dao.setSmsSent(id, part, if (result == Activity.RESULT_OK) 1 else -1)
                    else if (event == "DELIVERED") dao.setSmsDelivered(id, part, if (result == Activity.RESULT_OK) 1 else -1)
                    else return@withTransaction
                    val parts = dao.smsParts(id)
                    val next = when {
                        parts.any { it.sent == -1 } -> "FAILED: carrier send error $result"
                        parts.any { it.delivered == -1 } -> "FAILED: delivery report error $result"
                        parts.isNotEmpty() && parts.all { it.delivered == 1 } -> "DELIVERED"
                        parts.isNotEmpty() && parts.all { it.sent == 1 } -> "SENT"
                        else -> "SENDING"
                    }
                    if (next != item.status) {
                        dao.outboxStatus(id, if (next.startsWith("FAILED")) "FAILED" else next)
                        val payload = JSONObject(vault.decrypt(item.payload))
                        dao.insertLog(vault.protect(SmsLog(sender = payload.getString("sender"), message = payload.getString("message"),
                            ruleName = payload.getString("ruleName"), target = payload.getString("url"), status = next)))
                    }
                }
            } finally { pending.finish() }
        }
    }
}
