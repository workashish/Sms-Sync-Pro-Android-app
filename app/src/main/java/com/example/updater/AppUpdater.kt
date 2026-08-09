package com.example.updater

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.BuildConfig
import com.example.data.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore
) {
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val urlString = settings.updateUrl.first()
            if (urlString.isBlank()) return@withContext null
            
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                // GitHub Releases API check
                if (json.has("tag_name") && json.has("assets")) {
                    val tagName = json.getString("tag_name")
                    val assets = json.getJSONArray("assets")
                    if (assets.length() > 0) {
                        val downloadUrl = assets.getJSONObject(0).getString("browser_download_url")
                        val releaseNotes = json.optString("body", "")
                        
                        // Use string comparison or assume tag_name is like "v1.0.1"
                        val remoteVersion = tagName.replace(Regex("[^0-9.]"), "")
                        val currentVersion = BuildConfig.VERSION_NAME.replace(Regex("[^0-9.]"), "")
                        
                        // Simple check: if different, assume it's an update (in real app, use semantic versioning comparison)
                        if (remoteVersion != currentVersion && remoteVersion.isNotEmpty()) {
                            return@withContext UpdateInfo(
                                versionCode = 0,
                                versionName = tagName,
                                downloadUrl = downloadUrl,
                                releaseNotes = releaseNotes
                            )
                        }
                    }
                    return@withContext null
                }
                
                // Custom JSON check
                val remoteVersionCode = json.optInt("versionCode", -1)
                val currentVersionCode = BuildConfig.VERSION_CODE
                
                if (remoteVersionCode > currentVersionCode) {
                    return@withContext UpdateInfo(
                        versionCode = remoteVersionCode,
                        versionName = json.optString("versionName", "Unknown"),
                        downloadUrl = json.optString("downloadUrl", ""),
                        releaseNotes = json.optString("releaseNotes", "")
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
    
    fun downloadUpdate(url: String, version: String): Long {
        val request = DownloadManager.Request(Uri.parse(url))
        request.setTitle("SMS Sync Pro Update")
        request.setDescription("Downloading version $version...")
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "sms-sync-pro-$version.apk")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }
}
