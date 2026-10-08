package com.example.updater

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        val preferences = context.getSharedPreferences("updater", Context.MODE_PRIVATE)
        if (id == -1L || id != preferences.getLong("download_id", -1L)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val status = manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    if (cursor == null || !cursor.moveToFirst()) throw IllegalStateException("Download file unavailable.")
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                }
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val uri = manager.getUriForDownloadedFile(id) ?: throw IllegalStateException("Download file unavailable.")
                    val version = ApkVerifier.verify(context, uri, preferences.getString("expected_sha256", "") ?: "")
                    preferences.edit().putString("ready_uri", uri.toString()).putLong("ready_version", version).remove("download_error").remove("download_id").apply()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Update verified. Open Settings to install it.", Toast.LENGTH_LONG).show()
                        notifyReady(context)
                    }
                } else if (status == DownloadManager.STATUS_FAILED) throw IllegalStateException("Update download failed. Please retry.")
            } catch (e: Exception) {
                preferences.edit().remove("download_id").remove("ready_uri").putString("download_error", e.message ?: "Update verification failed.").apply()
                withContext(Dispatchers.Main) { Toast.makeText(context, e.message ?: "Unable to verify update.", Toast.LENGTH_LONG).show() }
            } finally { pending.finish() }
        }
    }
    private fun notifyReady(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("updates", "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, com.example.MainActivity::class.java).putExtra("open_settings", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val action = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(2, NotificationCompat.Builder(context, "updates").setSmallIcon(com.example.R.drawable.ic_stat_sms)
            .setContentTitle("SMS Sync Pro update ready").setContentText("Verified download. Tap to review and install.").setAutoCancel(true).setContentIntent(action).build())
    }
}
