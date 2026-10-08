package com.example.data
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ConfigImportTest {
    @Test fun validatesWholeFileBeforeAnyConfigurationIsApplied() {
        val valid = """{"settings":{"webhookTimeout":8,"globalEnable":true},"rules":[{"name":"Valid","type":"WEBHOOK","target":"https://example.com"}]}"""
        assertEquals(1, ConfigImport.parse(valid, false).rules.size)
        for (invalid in listOf(valid.replace("https://example.com", "http://example.com"), valid.replace("\"globalEnable\":true", "\"globalEnable\":\"yes\""), valid.replace("\"webhookTimeout\":8", "\"webhookTimeout\":0"), valid.replace("\"WEBHOOK\"", "\"INVALID\""))) {
            try { ConfigImport.parse(invalid, false); fail("Invalid import accepted") } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun legacyBackupCannotOverrideApkSettings() {
        val parsed = ConfigImport.parse("""{"settings":{"updateUrl":"http://untrusted.example","webhookSecret":"changed","aesEncryptionKey":"changed","webhookTimeout":15}}""", false)
        FixedSettings.keys.forEach { assertFalse(parsed.settings.has(it)) }
        assertEquals(15, parsed.settings.getInt("webhookTimeout"))
    }
    @Test fun fixedPolicyReplacesStoredOverridesWithoutChangingOtherSettings() {
        val defaults = JSONObject().put("webhookSecret", "YOUR_HMAC_SECRET_KEY").put("aesEncryptionKey", "YOUR_AES_PASSWORD")
        val saved = JSONObject().put("updateUrl", "https://untrusted.example").put("webhookSecret", "changed").put("aesEncryptionKey", "changed").put("webhookTimeout", 15)
        FixedSettings.applyTo(saved, defaults)
        assertEquals(FixedSettings.UPDATE_URL, saved.getString("updateUrl"))
        assertEquals("YOUR_HMAC_SECRET_KEY", saved.getString("webhookSecret"))
        assertEquals("YOUR_AES_PASSWORD", saved.getString("aesEncryptionKey"))
        assertEquals(15, saved.getInt("webhookTimeout"))
    }
}
