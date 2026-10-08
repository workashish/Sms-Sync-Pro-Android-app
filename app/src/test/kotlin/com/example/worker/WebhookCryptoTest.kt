package com.example.worker

import org.junit.Assert.assertEquals
import org.junit.Test

class WebhookCryptoTest {
    @Test fun matchesNodePbkdf2Sha256Fixture() {
        val key = WebhookCrypto.deriveKey("password", ByteArray(16) { 1 })
        assertEquals("92f1997ea77b70e4db72150a551d2723db78494f5b203266b2647e63094f7ebd", key.joinToString("") { "%02x".format(it.toInt() and 255) })
    }
}
