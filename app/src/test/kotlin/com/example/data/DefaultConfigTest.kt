package com.example.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DefaultConfigTest {
    @Test fun firstInstallSeedsDefaultRuleExactlyOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = DefaultConfig.load(context).getJSONObject("settings")
        assertTrue(config.getBoolean("retryFailedWebhooks"))
        assertTrue(config.getBoolean("enableSmsCommands"))
        assertEquals("YOUR_HMAC_SECRET_KEY", config.getString("webhookSecret"))
        assertEquals("YOUR_AES_PASSWORD", config.getString("aesEncryptionKey"))
        val db = AppDatabase.getDatabase(context)
        db.openHelper.writableDatabase.query("SELECT name, type, target, isActive FROM forwarding_rules").use {
            assertTrue(it.moveToFirst())
            assertEquals("sms sync dashboard", it.getString(0))
            assertEquals("WEBHOOK", it.getString(1))
            assertEquals("https://thesms.vercel.app/api/webhooks/incoming", it.getString(2))
            assertEquals(1, it.getInt(3))
            assertEquals(1, it.count)
        }
        AppDatabase.getDatabase(context).openHelper.writableDatabase.query("SELECT count(*) FROM forwarding_rules").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }
}
