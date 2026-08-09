package com.example.processor

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.SettingsDataStore
import com.example.data.SmsDao
import com.example.data.SmsLog
import com.example.worker.WebhookWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore,
    private val smsDao: SmsDao
) {
    suspend fun processMessage(sender: String, messageBody: String) {
        val globalEnable = settings.globalEnable.first()
        if (!globalEnable) return

        val enableSmsCommands = settings.enableSmsCommands.first()
        if (enableSmsCommands) {
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
                
                // Log command execution
                smsDao.insertLog(SmsLog(sender = sender, message = cmd, ruleName = "SYSTEM COMMAND", target = sender, status = "SUCCESS"))
                return
            } else if (cmd == "REBOOT" || cmd == "LOCATION") {
                val reply = "Command $cmd received but requires elevated permissions or root."
                forwardViaSms(context, sender, reply)
                smsDao.insertLog(SmsLog(sender = sender, message = cmd, ruleName = "SYSTEM COMMAND", target = sender, status = "FAILED: permission denied"))
                return
            }
        }

        val activeRules = smsDao.getActiveRules()
        
        // We run the rules processing in parallel
        kotlinx.coroutines.coroutineScope {
            activeRules.forEach { rule ->
                launch {
                    val keyword = rule.keywordFilter.trim()
                if (keyword.isNotEmpty()) {
                    val isMatch = if (keyword.startsWith("/") && (keyword.endsWith("/") || keyword.endsWith("/i"))) {
                        try {
                            val ignoreCase = keyword.endsWith("/i")
                            val regexPattern = if (ignoreCase) keyword.drop(1).dropLast(2) else keyword.drop(1).dropLast(1)
                            val regex = if (ignoreCase) Regex(regexPattern, RegexOption.IGNORE_CASE) else Regex(regexPattern)
                            withTimeoutOrNull(1000) {
                                regex.containsMatchIn(sender) || regex.containsMatchIn(messageBody)
                            } ?: false
                        } catch (e: Exception) {
                            messageBody.contains(keyword, ignoreCase = true) || sender.contains(keyword, ignoreCase = true)
                        }
                    } else {
                        messageBody.contains(keyword, ignoreCase = true) || sender.contains(keyword, ignoreCase = true)
                    }
                    
                    if (!isMatch) return@launch
                }

                if (rule.type == "SMS") {
                    var status = "SUCCESS"
                    try {
                        forwardViaSms(context, rule.target, "$sender:\n$messageBody")
                    } catch (e: Exception) {
                        status = "FAILED: ${e.message?.take(50)}"
                    }
                    smsDao.insertLog(
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
                        .setConstraints(constraints)
                        .setInputData(data)
                        .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 1, java.util.concurrent.TimeUnit.MINUTES)
                        .build()

                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "webhook_${rule.id}_${System.currentTimeMillis()}",
                        androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE,
                        workRequest
                    )
                }
            }
        }
    }
}
    
    private fun forwardViaSms(context: Context, target: String, message: String) {
        val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(android.telephony.SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            android.telephony.SmsManager.getDefault()
        }
        
        val parts = smsManager.divideMessage(message)
        if (parts.size > 1) {
            smsManager.sendMultipartTextMessage(target, null, parts, null, null)
        } else {
            smsManager.sendTextMessage(target, null, message, null, null)
        }
    }
}
