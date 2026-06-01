package com.example.worker

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import com.example.data.AppDatabase
import com.example.data.SmsLog
import com.example.data.SettingsManager
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class WebhookWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val urlString = inputData.getString("url") ?: return Result.failure()
        val sender = inputData.getString("sender") ?: return Result.failure()
        val message = inputData.getString("message") ?: return Result.failure()
        val ruleName = inputData.getString("ruleName") ?: "Manual Test"
        val isTest = inputData.getBoolean("isTest", false)
        
        val settings = SettingsManager(applicationContext)
        val deviceModel = if (settings.includeDeviceModel) Build.MODEL else "Unknown"
        val timeout = settings.webhookTimeout * 1000

        var success = false
        var exceptionMsg = ""

        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "SmsForwarder/1.0")
            connection.connectTimeout = timeout
            connection.readTimeout = timeout
            connection.doOutput = true

            var finalMessage = message
            if (settings.aesEncryptionKey.isNotEmpty()) {
                finalMessage = encryptAes(message, settings.aesEncryptionKey)
            }

            val jsonOutput = if (settings.customWebhookTemplate.isNotEmpty()) {
                settings.customWebhookTemplate
                    .replace("{sender}", JSONObject.quote(sender).removeSurrounding("\""))
                    .replace("{message}", JSONObject.quote(finalMessage).removeSurrounding("\""))
                    .replace("{device_model}", JSONObject.quote(deviceModel).removeSurrounding("\""))
            } else {
                JSONObject().apply {
                    put("sender", sender)
                    put("message", finalMessage)
                    if (settings.includeDeviceModel) {
                        put("device_model", deviceModel)
                    }
                    put("timestamp", System.currentTimeMillis())
                }.toString()
            }

            if (settings.webhookSecret.isNotEmpty()) {
                val signature = generateHmacSha256(jsonOutput, settings.webhookSecret)
                if (signature.isNotEmpty()) {
                    connection.setRequestProperty("X-Signature", signature)
                }
            }

            val writer = OutputStreamWriter(connection.outputStream, Charsets.UTF_8)
            writer.write(jsonOutput)
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                success = true
            } else {
                exceptionMsg = "HTTP Error: $responseCode"
            }
            connection.disconnect()
        } catch (e: Exception) {
            exceptionMsg = e.message ?: "Unknown Error"
        }

        if (!isTest) {
            val db = AppDatabase.getDatabase(applicationContext)
            
            if (success) {
                db.smsDao().insertLog(
                    SmsLog(sender = sender, message = message, ruleName = ruleName, target = urlString, status = "SUCCESS")
                )
                return Result.success()
            } else {
                if (runAttemptCount < 3 && settings.retryFailedWebhooks) {
                    return Result.retry()
                } else {
                    db.smsDao().insertLog(
                        SmsLog(sender = sender, message = message, ruleName = ruleName, target = urlString, status = "FAILED: $exceptionMsg")
                    )
                    return Result.failure()
                }
            }
        } else {
            // Test webhook, just return success/fail based on payload, don't insert DB log (or do? User didn't say, but DB log is nice).
            return if (success) Result.success() else Result.failure(Data.Builder().putString("error", exceptionMsg).build())
        }
    }

    private fun generateHmacSha256(data: String, key: String): String {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
            mac.init(secretKeySpec)
            val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
            hmacBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    private fun encryptAes(data: String, key: String): String {
        return try {
            val secretKey = SecretKeySpec(java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)), "AES")
            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            val combined = iv + ciphertext
            android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            "ENCRYPTION_FAILED"
        }
    }
}

