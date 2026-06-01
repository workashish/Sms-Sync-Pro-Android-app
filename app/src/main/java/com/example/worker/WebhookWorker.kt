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
            success = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var finalUrlString = urlString
                if (!finalUrlString.startsWith("http://") && !finalUrlString.startsWith("https://")) {
                    finalUrlString = "https://$finalUrlString"
                }
                val url = URL(finalUrlString)
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

                val jsonOutput = if (settings.customWebhookTemplate.isNotBlank()) {
                    settings.customWebhookTemplate
                        .replace("{sender}", JSONObject.quote(sender).removeSurrounding("\""))
                        .replace("{message}", JSONObject.quote(finalMessage).removeSurrounding("\""))
                        .replace("{body}", JSONObject.quote(finalMessage).removeSurrounding("\""))
                        .replace("{device_model}", JSONObject.quote(deviceModel).removeSurrounding("\""))
                } else {
                    val lowerMsg = message.lowercase()
                    val isOtpWord = lowerMsg.contains("code") || lowerMsg.contains("otp") || lowerMsg.contains("2fa") || lowerMsg.contains("verification")
                    val isBankWord = lowerMsg.contains("bank") || lowerMsg.contains("alert") || lowerMsg.contains("deposit") || lowerMsg.contains("payment") || lowerMsg.contains("account") || lowerMsg.contains("card") || lowerMsg.contains("debited") || lowerMsg.contains("credited") || lowerMsg.contains("emi") || lowerMsg.contains("balance") || lowerMsg.contains("due")

                    val type = when {
                        isOtpWord -> "otp"
                        isBankWord -> "bank"
                        else -> "message"
                    }
                    
                    JSONObject().apply {
                        put("type", type)
                        put("sender", sender)
                        put("body", finalMessage)
                        put("time", java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(System.currentTimeMillis())))
                        
                        val metaObj = JSONObject()
                        if (type == "otp") {
                            val codeMatcher = Regex("\\b\\d{4,8}\\b|G-\\d{6}").find(message)
                            if (codeMatcher != null) {
                                metaObj.put("code", codeMatcher.value)
                            }
                        } else if (type == "bank") {
                            if (lowerMsg.contains("deposit") || lowerMsg.contains("credited")) {
                                metaObj.put("bank_type", "DEPOSIT")
                            } else {
                                metaObj.put("bank_type", "PAYMENT")
                            }
                            val amountMatcher = Regex("\\$?\\s*\\d+(?:,\\d{3})*(?:\\.\\d{2})?").find(message)
                            if (amountMatcher != null) {
                                metaObj.put("amount", amountMatcher.value.replace("$", "").trim())
                            }
                        }
                        if (settings.includeDeviceModel) {
                            metaObj.put("device_model", deviceModel)
                        }
                        if (metaObj.length() > 0) {
                            put("metadata", metaObj)
                        }
                    }.toString().replace("\\/", "/")
                }

                if (settings.webhookSecret.isNotEmpty()) {
                    val signature = generateHmacSha256(jsonOutput, settings.webhookSecret)
                    if (signature.isNotEmpty()) {
                        connection.setRequestProperty("x-hmac-signature", signature)
                    }
                }

                val jsonBytes = jsonOutput.toByteArray(Charsets.UTF_8)
                connection.setRequestProperty("Content-Length", jsonBytes.size.toString())
                connection.outputStream.write(jsonBytes)
                connection.outputStream.flush()
                connection.outputStream.close()

                val responseCode = connection.responseCode
                var isSuccess = false
                if (responseCode in 200..299) {
                    isSuccess = true
                } else {
                    val errorStream = connection.errorStream
                    var errorBody = errorStream?.bufferedReader()?.use { it.readText() }?.trim() ?: ""
                    
                    // If error is HTML (common for 404s/500s from web servers), strip it or suppress it
                    if (errorBody.contains("<html", ignoreCase = true) || errorBody.contains("<!doctype", ignoreCase = true)) {
                        errorBody = "Server returned HTML page instead of API response. Please check if your Webhook URL is correct."
                    } else if (errorBody.length > 250) {
                        errorBody = errorBody.take(250) + "..."
                    }
                    
                    val statusText = when (responseCode) {
                        404 -> "404 Not Found (Check URL)"
                        401 -> "401 Unauthorized (Check HMAC/AES Secret)"
                        400 -> "400 Bad Request"
                        else -> "$responseCode"
                    }
                    exceptionMsg = "HTTP Error: $statusText - $errorBody\nPayload Sent: $jsonOutput"
                }
                connection.disconnect()
                isSuccess
            }
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
            // Test webhook: insert DB log so user can see it in Logs tab.
            val db = AppDatabase.getDatabase(applicationContext)
            db.smsDao().insertLog(
                SmsLog(sender = sender, message = message, ruleName = ruleName, target = urlString, status = if (success) "SUCCESS" else "FAILED: $exceptionMsg")
            )
            return if (success) Result.success() else Result.failure(Data.Builder().putString("error", exceptionMsg).build())
        }
    }

    private fun generateHmacSha256(data: String, key: String): String {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
            mac.init(secretKeySpec)
            val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
            hmacBytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
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
            val ivHex = iv.joinToString("") { "%02x".format(it) }
            val cipherHex = ciphertext.joinToString("") { "%02x".format(it) }
            "$ivHex:$cipherHex"
        } catch (e: Exception) {
            "ENCRYPTION_FAILED"
        }
    }
}

