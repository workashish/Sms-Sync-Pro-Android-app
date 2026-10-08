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
}
