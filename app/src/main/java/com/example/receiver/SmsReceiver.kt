package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.work.*
import com.example.data.AppDatabase
import com.example.data.SettingsDataStore
import com.example.data.SmsDao
import com.example.data.SmsLog
import com.example.worker.WebhookWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SmsReceiverEntryPoint {
        fun messageProcessor(): com.example.processor.MessageProcessor
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Sort PDU parts by index to handle multipart SMS
        messages.sortBy { it.indexOnIcc }

        val hiltEntryPoint = EntryPointAccessors.fromApplication(context.applicationContext, SmsReceiverEntryPoint::class.java)
        val processor = hiltEntryPoint.messageProcessor()

        val pendingResult = goAsync()
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        coroutineScope.launch {
            try {
                val sender = messages[0].displayOriginatingAddress ?: "Unknown"
                val messageBody = messages.joinToString(separator = "") { it.messageBody ?: "" }
                processor.processMessage(sender, messageBody)
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error processing SMS", e)
            } finally {
                pendingResult.finish()
                coroutineScope.cancel()
            }
        }
    }
}
