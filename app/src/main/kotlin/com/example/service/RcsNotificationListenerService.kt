package com.example.service

import android.app.Notification
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.SettingsDataStore
import com.example.processor.MessageProcessor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class RcsNotificationListenerService : NotificationListenerService() {
    @Inject lateinit var processor: MessageProcessor
    @Inject lateinit var settings: SettingsDataStore
    private val cacheMutex = Mutex()
    private val cache by lazy { getSharedPreferences("notification_dedupe", android.content.Context.MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null) scope.launch { cacheMutex.withLock { cache.edit().remove(processor.fingerprint(sbn.key)).apply() } }
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName !in setOf("com.google.android.apps.messaging", "com.samsung.android.messaging")) return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 || notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        val extras = notification.extras
        val newest = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)?.messages?.maxByOrNull { it.timestamp }
        if (newest != null && newest.person == null) return // MessagingStyle uses null for the local user.
        val sender = newest?.person?.name?.toString() ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val body = newest?.text?.toString() ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        val time = newest?.timestamp ?: sbn.postTime
        if (sender.isBlank() || body.isBlank()) return
        scope.launch {
            try {
                if (!settings.captureRcs.first()) return@launch
                cacheMutex.withLock {
                    val key = processor.fingerprint(sbn.key)
                    val hash = processor.fingerprint(body)
                    if (newest == null && cache.getString(key, null) == hash) return@withLock
                    processor.processMessage(sender, body, time, "NOTIFICATION", "${sbn.key}|$time|$hash")
                    if (newest == null) cache.edit().putString(key, hash).apply()
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { android.util.Log.e("SmsSyncNotifications", "Unable to persist notification") }
        }
    }
}
