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
import kotlinx.coroutines.CancellationException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@HiltWorker
class WebhookWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settings: SettingsDataStore,
    private val smsDao: SmsDao,
    private val vault: com.example.data.LocalVault
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val queued = inputData.getString("outboxId")?.let { smsDao.outbox(it) }
        if (queued != null && queued.status != "PENDING") return Result.success()
        val payload = queued?.let { JSONObject(vault.decrypt(it.payload)) }
        val urlString = payload?.getString("url") ?: inputData.getString("url") ?: return Result.failure()
        val sender = payload?.getString("sender") ?: inputData.getString("sender") ?: return Result.failure()
        val message = payload?.getString("message") ?: inputData.getString("message") ?: return Result.failure()
        val ruleName = payload?.optString("ruleName") ?: inputData.getString("ruleName") ?: "Manual Test"
        val isTest = queued?.isTest ?: inputData.getBoolean("isTest", false)
        val config = settings.snapshot()
        if (!isTest && !config.getBoolean("globalEnable")) return Result.retry()
        val messageId = queued?.id ?: id.toString()
        val includeDeviceModel = config.getBoolean("includeDeviceModel")
        val deviceModel = if (includeDeviceModel) Build.MODEL else "Unknown"
        val timeout = config.getInt("webhookTimeout").coerceIn(1, 60) * 1000
        val retryFailed = config.getBoolean("retryFailedWebhooks")
        val customTemplate = config.getString("customWebhookTemplate")
        val aesEncryptionKey = config.getString("aesEncryptionKey")
        val webhookSecret = config.getString("webhookSecret")
        val receivedAt = (queued?.timestamp ?: inputData.getLong("timestamp", 0)).takeIf { it > 0 } ?: System.currentTimeMillis()
        val encryption = if (aesEncryptionKey.isEmpty()) "none" else "aes-256-gcm-pbkdf2-sha256-v1"

        var success = false
        var exceptionMsg = ""
        var retryable = true

        queued?.let { smsDao.attempt(it.id, System.currentTimeMillis()) }
        try {
            success = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var finalUrlString = urlString
                if (!finalUrlString.contains("://")) finalUrlString = "https://$finalUrlString"
                val parsedUrl = URL(finalUrlString)
                require(parsedUrl.protocol == "https" || (com.example.BuildConfig.DEBUG && parsedUrl.protocol == "http")) {
                    "Only HTTPS is supported for Webhooks"
                }
                var finalMessage = message
                if (aesEncryptionKey.isNotEmpty()) {
                    finalMessage = encryptAesGcm(message, aesEncryptionKey)
                }

                val jsonOutput = if (customTemplate.isNotBlank()) {
                    WebhookPayload.renderTemplate(customTemplate, sender, finalMessage, deviceModel, messageId, receivedAt, encryption).let { rendered ->
                        if (parsedUrl.path.endsWith("/api/webhooks/incoming")) {
                            val objectPayload = JSONObject(rendered)
                            require(objectPayload.optString("sender") == sender && objectPayload.optString("body") == finalMessage) { "Dashboard templates must include sender and body placeholders." }
                            objectPayload.put("id", messageId).put("schema_version", 1).put("encryption", encryption).put("timestamp", receivedAt).toString()
                        } else rendered
                    }
                } else {
                    val parsed = com.example.processor.MessageClassification.parse(message)
                    val type = parsed.type
                    JSONObject().apply {
                        put("id", messageId)
                        put("schema_version", 1)
                        put("encryption", if (aesEncryptionKey.isEmpty()) "none" else "aes-256-gcm-pbkdf2-sha256-v1")
                        put("type", type)
                        put("sender", sender)
                        put("body", finalMessage)
                        put("timestamp", receivedAt)
                        put("time", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }.format(java.util.Date(receivedAt)))
                        
                        val metaObj = com.example.processor.MessageClassification.metadata(parsed, aesEncryptionKey.isNotEmpty())
                        if (includeDeviceModel) {
                            metaObj.put("device_model", deviceModel)
                        }
                        if (aesEncryptionKey.isNotEmpty()) {
                            metaObj.remove("code")
                            metaObj.remove("amount")
                        }
                        if (metaObj.length() > 0) {
                            put("metadata", metaObj)
                        }
                    }.toString().replace("\\/", "/")
                }

                val signature = if (webhookSecret.isNotEmpty()) generateHmacSha256(jsonOutput, webhookSecret) else ""
                val response = WebhookTransport.send(finalUrlString, jsonOutput, signature, messageId, timeout.toLong())
                retryable = response.status == 408 || response.status == 429 || response.status in 500..599
                if (response.status !in 200..299) exceptionMsg = "HTTP ${response.status}: ${response.error}"
                response.status in 200..299
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            retryable = e is java.io.IOException
            exceptionMsg = e.message ?: "Unknown Error"
        }

        if (!isTest) {
            if (success) {
                smsDao.insertLog(
                    vault.protect(SmsLog(sender = sender, message = message, ruleName = ruleName, target = urlString, status = "SUCCESS"))
                )
                queued?.let { smsDao.outboxStatus(it.id, "SUCCESS") }
                return Result.success()
            } else {
                if ((queued?.attempts ?: runAttemptCount) < 3 && retryFailed && retryable) {
                    smsDao.insertLog(vault.protect(SmsLog(sender = sender, message = message, ruleName = ruleName, target = urlString, status = "RETRYING: $exceptionMsg")))
                    return Result.retry()
                } else {
                    smsDao.insertLog(
                        vault.protect(SmsLog(sender = sender, message = message, ruleName = ruleName, target = urlString, status = "FAILED: $exceptionMsg"))
                    )
                    queued?.let { smsDao.outboxStatus(it.id, "FAILED") }
                    return Result.failure()
                }
            }
        } else {
            smsDao.insertLog(
                vault.protect(SmsLog(sender = sender, message = message, ruleName = ruleName, target = urlString, status = if (success) "SUCCESS" else "FAILED: $exceptionMsg"))
            )
            queued?.let { smsDao.outboxStatus(it.id, if (success) "SUCCESS" else "FAILED") }
            return if (success) Result.success() else Result.failure(Data.Builder().putString("error", exceptionMsg).build())
        }
    }

    private fun generateHmacSha256(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun encryptAesGcm(data: String, key: String): String {
        // PBKDF2 with HMAC-SHA256
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val secretKey = SecretKeySpec(WebhookCrypto.deriveKey(key, salt), "AES")

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

