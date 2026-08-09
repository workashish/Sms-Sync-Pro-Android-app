package com.example.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId != -1L) {
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = manager.query(query)
                
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    
                    if (statusIndex != -1) {
                        val status = cursor.getInt(statusIndex)
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val uri = manager.getUriForDownloadedFile(downloadId)
                            if (uri != null) {
                                installApk(context, uri)
                            } else {
                                Toast.makeText(context, "Failed to get download URI.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Update download failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                cursor.close()
            }
        }
    }

    private fun installApk(context: Context, uri: Uri) {
        try {
            var fileUri = uri
            if (uri.scheme == "file") {
                val file = File(uri.path!!)
                fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to install update.", Toast.LENGTH_SHORT).show()
        }
    }
}
