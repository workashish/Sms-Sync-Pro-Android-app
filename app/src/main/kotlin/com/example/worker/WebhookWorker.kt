package com.example.worker

import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import com.example.data.SettingsDataStore
import com.example.data.SmsDao
import com.example.data.SmsLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@HiltWorker
class WebhookWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settings: SettingsDataStore,
    private val smsDao: SmsDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val urlString = inputData.getString("url") ?: return Result.failure()
        val sender = inputData.getString("sender") ?: return Result.failure()
        val message = inputData.getString("message") ?: return Result.failure()
        val ruleName = inputData.getString("ruleName") ?: "Manual Test"
        val isTest = inputData.getBoolean("isTest", false)
        
        val includeDeviceModel = settings.includeDeviceModel.first()
        val deviceModel = if (includeDeviceModel) Build.MODEL else "Unknown"
        val timeout = settings.webhookTimeout.first() * 1000
        val retryFailed = settings.retryFailedWebhooks.first()
        val customTemplate = settings.customWebhookTemplate.first()
        val aesEncryptionKey = settings.getAesEncryptionKey()
        val webhookSecret = settings.getWebhookSecret()

        var success = false
        var exceptionMsg = ""

        try {
            success = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var finalUrlString = urlString
                if (!finalUrlString.startsWith("https://")) {
                    if (finalUrlString.startsWith("http://")) {
                        throw IllegalArgumentException("Only HTTPS is supported for Webhooks")
                    }
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
                if (aesEncryptionKey.isNotEmpty()) {
                    finalMessage = encryptAesGcm(message, aesEncryptionKey)
                }

                val jsonOutput = if (customTemplate.isNotBlank()) {
                    val jsonObj = try { JSONObject(customTemplate) } catch(e: Exception) { JSONObject() }
                    
                    val keys = jsonObj.keys()
                    while(keys.hasNext()) {
                        val k = keys.next()
                        val v = jsonObj.optString(k)
                        if (v.contains("{sender}")) jsonObj.put(k, v.replace("{sender}", sender))
                        if (v.contains("{message}")) jsonObj.put(k, v.replace("{message}", finalMessage))
                        if (v.contains("{body}")) jsonObj.put(k, v.replace("{body}", finalMessage))
                        if (v.contains("{device_model}")) jsonObj.put(k, v.replace("{device_model}", deviceModel))
                    }
                    if (jsonObj.length() == 0) {
                        customTemplate
                            .replace("{sender}", JSONObject.quote(sender).removeSurrounding("\""))
                            .replace("{message}", JSONObject.quote(finalMessage).removeSurrounding("\""))
                            .replace("{body}", JSONObject.quote(finalMessage).removeSurrounding("\""))
                            .replace("{device_model}", JSONObject.quote(deviceModel).removeSurrounding("\""))
                    } else {
                        jsonObj.toString()
                    }
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
                        if (includeDeviceModel) {
                            metaObj.put("device_model", deviceModel)
                        }
                        if (metaObj.length() > 0) {
                            put("metadata", metaObj)
                        }
                    }.toString().replace("\\/", "/")
                }

                if (webhookSecret.isNotEmpty()) {
                    val signature = generateHmacSha256(jsonOutput, webhookSecret)
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
                    exceptionMsg = "HTTP Error: $statusText - $errorBody"
                }
                connection.disconnect()
                isSuccess
            }
        } catch (e: Exception) {
            exceptionMsg = e.message ?: "Unknown Error"
        }

        if (!isTest) {
            if (success) {
                smsDao.insertLog(
                    SmsLog(sender = sender, message = message, ruleName = ruleName, target = urlString, status = "SUCCESS")
                )
                return Result.success()
            } else {
                if (runAttemptCount < 3 && retryFailed) {
                    return Result.retry()
                } else {
                    smsDao.insertLog(
                        SmsLog(sender = sender, message = message, ruleName = ruleName, target = urlString, status = "FAILED: $exceptionMsg")
                    )
                    return Result.failure()
                }
            }
        } else {
            smsDao.insertLog(
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

    private fun encryptAesGcm(data: String, key: String): String {
        // PBKDF2 with HMAC-SHA256
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val spec = PBEKeySpec(key.toCharArray(), salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        
        val ciphertext = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val ivHex = iv.joinToString("") { "%02x".format(it) }
        val cipherHex = ciphertext.joinToString("") { "%02x".format(it) }
        
        return "$saltHex:$ivHex:$cipherHex"
    }
}

