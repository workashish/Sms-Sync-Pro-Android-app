package com.example.worker

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object WebhookCrypto {
    // One 32-byte PBKDF2-HMAC-SHA256 block, compatible with Android 5+.
    // Android's PBKDF2WithHmacSHA256 SecretKeyFactory only exists from API 26.
    fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        var block = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val key = block.copyOf()
        repeat(9999) {
            block = mac.doFinal(block)
            for (index in key.indices) key[index] = (key[index].toInt() xor block[index].toInt()).toByte()
        }
        return key
    }
}
