package com.example.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.io.File
import java.security.MessageDigest

object ApkVerifier {
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo, history: Boolean): Set<String> = if (Build.VERSION.SDK_INT >= 28) {
        val signing = info.signingInfo ?: throw IllegalArgumentException("APK signing information is missing.")
        (if (history && !signing.hasMultipleSigners()) signing.signingCertificateHistory else signing.apkContentsSigners).map { digest(it.toByteArray()) }.toSet()
    } else (info.signatures ?: emptyArray()).map { digest(it.toByteArray()) }.toSet()
    fun verify(context: Context, uri: Uri, expected: String): Long {
        UpdateMetadata.checksum(expected)
        val file = File.createTempFile("verify_update_", ".apk", context.cacheDir)
        try {
            val sha = MessageDigest.getInstance("SHA-256")
            val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Update file unavailable.")
            input.use { source -> file.outputStream().use { output ->
                val buffer = ByteArray(65536); var bytes = 0L
                while (true) {
                    val size = source.read(buffer); if (size < 0) break
                    bytes += size; require(bytes <= 100L * 1024 * 1024) { "Update APK exceeds 100 MB." }
                    sha.update(buffer, 0, size); output.write(buffer, 0, size)
                }
            } }
            val actual = sha.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            require(actual.equals(expected, true)) { "Update checksum mismatch. Delete the download and retry." }
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
            @Suppress("DEPRECATION")
            val incoming = context.packageManager.getPackageArchiveInfo(file.path, flags) ?: throw IllegalArgumentException("Invalid APK file.")
            @Suppress("DEPRECATION")
            val installed = context.packageManager.getPackageInfo(context.packageName, flags)
            require(incoming.packageName == context.packageName) { "Downloaded APK belongs to a different application." }
            val incomingVersion = if (Build.VERSION.SDK_INT >= 28) incoming.longVersionCode else @Suppress("DEPRECATION") incoming.versionCode.toLong()
            val currentVersion = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else @Suppress("DEPRECATION") installed.versionCode.toLong()
            require(incomingVersion > currentVersion) { "Downloaded APK is not a newer version." }
            val current = signatures(installed, false)
            require(current.isNotEmpty() && (if (current.size > 1) signatures(incoming, false) == current else signatures(incoming, true).containsAll(current))) { "APK signing key does not match this installation." }
            if (!com.example.BuildConfig.DEBUG) require((incoming.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) { "Debug APK cannot replace this release installation." }
            return incomingVersion
        } finally { file.delete() }
    }
}
