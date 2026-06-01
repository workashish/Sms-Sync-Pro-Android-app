package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.SmsLog
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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

        val pendingResult = goAsync()

        // For production, WorkManager is recommended, but this handles quick requests.
        // Process rules in parallel to avoid hitting the 10s BroadcastReceiver timeout if there are multiple webhooks.
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

                        var status = "SUCCESS"
                        try {
                            if (rule.type == "SMS") {
                                forwardViaSms(context, rule.target, "$sender:\n$messageBody")
                            } else if (rule.type == "WEBHOOK") {
                                forwardViaWebhook(rule.target, sender, messageBody, settings)
                            }
                        } catch (e: Exception) {
                            Log.e("SmsReceiver", "Failed to forward via rule ${rule.name}", e)
                            status = "FAILED: ${e.message?.take(50)}"
                        }

                        // Log to DB
                        db.smsDao().insertLog(
                            SmsLog(
                                sender = sender,
                                message = messageBody,
                                ruleName = rule.name,
                                target = rule.target,
                                status = status
                            )
                        )
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

    private fun generateHmacSha256(data: String, key: String): String {
        try {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
            mac.init(secretKeySpec)
            val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
            return hmacBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            return ""
        }
    }

    private fun forwardViaWebhook(urlString: String, sender: String, message: String, settings: com.example.data.SettingsManager) {
        var success = false
        val maxAttempts = if (settings.retryFailedWebhooks) 3 else 1
        var attempt = 0
        var lastException: Exception? = null

        while (attempt < maxAttempts && !success) {
            attempt++
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = settings.webhookTimeout * 1000
                connection.readTimeout = settings.webhookTimeout * 1000
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.doOutput = true

                val jsonOutput = JSONObject().apply {
                    put("sender", sender)
                    put("message", message)
                    if (settings.includeDeviceModel) {
                        put("device_model", android.os.Build.MODEL)
                    }
                    put("timestamp", System.currentTimeMillis())
                }.toString()

                if (settings.webhookSecret.isNotEmpty()) {
                    val signature = generateHmacSha256(jsonOutput, settings.webhookSecret)
                    if (signature.isNotEmpty()) {
                        connection.setRequestProperty("X-Signature", signature)
                    }
                }

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonOutput)
                writer.flush()
                writer.close()

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw Exception("HTTP Error: $responseCode")
                }
                success = true
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxAttempts) {
                    Thread.sleep(1000) // basic backoff
                }
            } finally {
                connection.disconnect()
            }
        }
        
        if (!success) {
            throw lastException ?: Exception("Unknown forwarding error")
        }
    }
}
