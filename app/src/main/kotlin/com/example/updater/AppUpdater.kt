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
        val urlString = settings.updateUrl.first()
        require(urlString.isNotBlank()) { "Configure an update URL first." }
        val connection = URL(UpdateMetadata.secureUrl(urlString)).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "SmsSyncPro/${BuildConfig.VERSION_NAME}")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val status = connection.responseCode
            if (status == 404) throw IllegalStateException("No published release found. Publish a signed APK to GitHub Releases first.")
            if (status == 403 || status == 429) throw IllegalStateException("Update server is rate limited or access is denied. Try again later.")
            if (status != 200) throw IllegalStateException("Update server returned HTTP $status. Try again later.")
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            UpdateMetadata.parse(JSONObject(response), BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Unable to reach the update server. Check your internet connection and retry.", e)
        } catch (e: org.json.JSONException) {
            throw IllegalStateException("The update server returned invalid metadata.", e)
        } finally { connection.disconnect() }
    }

    fun downloadUpdate(url: String, version: String, sha256: String): Long {
        UpdateMetadata.secureUrl(url)
        UpdateMetadata.checksum(sha256)
        val safeVersion = version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val preferences = context.getSharedPreferences("updater", Context.MODE_PRIVATE)
        require(preferences.getLong("download_id", -1L) == -1L) { "An update is already downloading." }
        val request = DownloadManager.Request(Uri.parse(url))
        request.setTitle("SMS Sync Pro Update")
        request.setDescription("Downloading version $version...")
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "sms-sync-pro-$safeVersion-${System.currentTimeMillis()}.apk")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = manager.enqueue(request)
        preferences.edit().putLong("download_id", id).putString("expected_sha256", sha256).remove("ready_uri").remove("download_error").apply()
        return id
    }
}
