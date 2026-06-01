package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.AppDatabase
import com.example.data.SmsLog
import com.example.worker.WebhookWorker
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return
        
        val settings = com.example.data.SettingsManager(context)
        if (!settings.globalEnable) {
            Log.d("SmsReceiver", "Global forwarding is disabled in settings.")
            return
        }

        val sender = messages[0].displayOriginatingAddress ?: "Unknown"
        val messageBody = messages.joinToString(separator = "") { it.messageBody ?: "" }

        Log.d("SmsReceiver", "Received SMS from $sender: $messageBody")
        
        if (settings.enableSmsCommands) {
            val cmd = messageBody.trim().uppercase()
            if (cmd == "STATUS") {
                val batteryStatus = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else -1
                
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
                val networkName = try { telephonyManager.networkOperatorName } catch(e: Exception) { "Unknown" }
                
                val reply = "STATUS\nBattery: $batteryPct%\nNetwork: $networkName"
                forwardViaSms(context, sender, reply)
                return
            } else if (cmd == "REBOOT" || cmd == "LOCATION") {
                val reply = "Command $cmd received but requires elevated permissions or root."
                forwardViaSms(context, sender, reply)
                return
            }
        }

        val pendingResult = goAsync()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val activeRules = db.smsDao().getActiveRules()

                val jobs = activeRules.map { rule ->
                    launch {
                        // Check keyword filter
                        val keyword = rule.keywordFilter.trim()
                        if (keyword.isNotEmpty() && !messageBody.contains(keyword, ignoreCase = true) && !sender.contains(keyword, ignoreCase = true)) {
                            return@launch
                        }

                        if (rule.type == "SMS") {
                            var status = "SUCCESS"
                            try {
                                forwardViaSms(context, rule.target, "$sender:\n$messageBody")
                            } catch (e: Exception) {
                                status = "FAILED: ${e.message?.take(50)}"
                            }
                            db.smsDao().insertLog(
                                SmsLog(
                                    sender = sender,
                                    message = messageBody,
                                    ruleName = rule.name,
                                    target = rule.target,
                                    status = status
                                )
                            )
                        } else if (rule.type == "WEBHOOK") {
                            val data = Data.Builder()
                                .putString("url", rule.target)
                                .putString("sender", sender)
                                .putString("message", messageBody)
                                .putString("ruleName", rule.name)
                                .putBoolean("isTest", false)
                                .build()

                            val constraints = Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()

                            val workRequest = OneTimeWorkRequestBuilder<WebhookWorker>()
                                .setInputData(data)
                                .setConstraints(constraints)
                                // WorkManager internal backoff strategy
                                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                                .build()

                            WorkManager.getInstance(context).enqueue(workRequest)
                            
                            // Let the worker handle logging to the database!
                        }
                    }
                }
                jobs.forEach { it.join() }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error processing SMS rules", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun forwardViaSms(context: Context, targetNumber: String, message: String) {
        val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        
        smsManager?.let {
            val parts = it.divideMessage(message)
            it.sendMultipartTextMessage(targetNumber, null, parts, null, null)
        }
    }
}
