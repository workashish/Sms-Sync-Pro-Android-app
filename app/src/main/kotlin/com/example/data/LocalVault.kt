package com.example.data

import android.content.Context
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import javax.security.auth.x500.X500Principal

@Singleton
class LocalVault @Inject constructor(@ApplicationContext private val context: Context) {
    private val key: SecretKey by lazy { loadKey() }
    private fun loadKey(): SecretKey = synchronized(KEY_LOCK) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (Build.VERSION.SDK_INT >= 23) {
            if (!store.containsAlias(ALIAS)) {
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                    init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                }.generateKey()
            }
            return@synchronized store.getKey(ALIAS, null) as SecretKey
        }
        // Android 5 supports RSA in Keystore; wrap the randomly generated AES key.
        val legacyAlias = "${ALIAS}_rsa"
        if (!store.containsAlias(legacyAlias)) {
            val start = Calendar.getInstance()
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
            @Suppress("DEPRECATION")
            val spec = KeyPairGeneratorSpec.Builder(context).setAlias(legacyAlias)
                .setSubject(X500Principal("CN=SMS Sync local encryption"))
                .setSerialNumber(BigInteger.ONE).setStartDate(start.time).setEndDate(end.time).build()
            KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply { initialize(spec) }.generateKeyPair()
        }
        val prefs = context.getSharedPreferences("local_vault", Context.MODE_PRIVATE)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        val wrapped = prefs.getString("wrapped_key", null)
        if (wrapped != null) {
            cipher.init(Cipher.DECRYPT_MODE, store.getKey(legacyAlias, null))
            SecretKeySpec(cipher.doFinal(Base64.decode(wrapped, Base64.NO_WRAP)), "AES")
        } else {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, store.getCertificate(legacyAlias).publicKey)
            check(prefs.edit().putString("wrapped_key", Base64.encodeToString(cipher.doFinal(bytes), Base64.NO_WRAP)).commit())
            SecretKeySpec(bytes, "AES")
        }
    }
    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return "local:v1:" + Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun decrypt(value: String): String {
        if (!value.startsWith("local:v1:")) return value // Legacy logs migrated on database upgrade.
        val bytes = Base64.decode(value.removePrefix("local:v1:"), Base64.NO_WRAP)
        require(bytes.size >= 28) { "Invalid encrypted local record" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
    fun protect(log: SmsLog) = log.copy(sender = encrypt(log.sender), message = encrypt(log.message), target = encrypt(log.target), ruleName = encrypt(log.ruleName), status = encrypt(log.status))
    fun reveal(log: SmsLog) = log.copy(sender = decrypt(log.sender), message = decrypt(log.message), target = decrypt(log.target), ruleName = decrypt(log.ruleName), status = decrypt(log.status))
    companion object { private const val ALIAS = "sms_sync_local_v1"; private val KEY_LOCK = Any() }
}
