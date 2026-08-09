package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.SettingsDataStore
import com.example.processor.MessageProcessor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RcsNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var messageProcessor: MessageProcessor
    @Inject lateinit var settings: SettingsDataStore

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        // Check for common messaging apps that support RCS
        if (packageName == "com.google.android.apps.messaging" || packageName == "com.samsung.android.messaging") {
            val extras = sbn.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE) ?: return
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

            // Ensure we are not processing our own notifications
            if (title.isBlank() || text.isBlank()) return
            
            // To avoid duplicates with standard SMS receiver, you might want to filter or 
            // only process this if the user explicitly enabled RCS capturing.
            // Google Messages sometimes posts standard SMS as notifications too, which might 
            // cause duplicate webhook calls if SMS_RECEIVED also triggered.
            // Ideally, we'd distinguish RCS, but notification extras don't strictly define it.
            
            CoroutineScope(Dispatchers.IO).launch {
                val captureRcs = settings.captureRcs.first()
                if (captureRcs) {
                    messageProcessor.processMessage(title, text)
                }
            }
        }
    }
}
