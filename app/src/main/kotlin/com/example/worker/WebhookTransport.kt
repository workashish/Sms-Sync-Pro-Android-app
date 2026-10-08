package com.example.worker

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

object WebhookTransport {
    private val base = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    data class Response(val status: Int, val error: String)
    fun send(url: String, body: String, signature: String, messageId: String, timeoutMillis: Long): Response {
        val client = base.newBuilder().callTimeout(timeoutMillis, TimeUnit.MILLISECONDS).connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS).readTimeout(timeoutMillis, TimeUnit.MILLISECONDS).build()
        val request = Request.Builder().url(url).post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json").header("User-Agent", "SmsSyncPro/${com.example.BuildConfig.VERSION_NAME}")
            .header("Idempotency-Key", messageId).apply { if (signature.isNotEmpty()) header("x-hmac-signature", signature) }.build()
        return client.newCall(request).execute().use { response ->
            Response(response.code, if (response.isSuccessful) "" else response.peekBody(4096).string().take(250))
        }
    }
}
