package com.example.worker

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class WebhookPayloadTest {
    @Test fun replacesAllPlaceholdersWithoutLosingEarlierReplacements() {
        val output = JSONObject(WebhookPayload.renderTemplate(
            """{"text":"{sender}: {message} ({device_model})","count":3,"enabled":true}""",
            "BANK", "OTP 123456", "Pixel"
        ))
        assertEquals("BANK: OTP 123456 (Pixel)", output.getString("text"))
        assertEquals(3, output.getInt("count"))
        assertEquals(true, output.getBoolean("enabled"))
    }

    @Test fun rendersNestedArraysAndEscapesMessageText() {
        val message = "Quote: \"hi\"\nनमस्ते {sender}"
        val output = JSONObject(WebhookPayload.renderTemplate(
            """{"embeds":[{"description":"{body}"}],"sender":"{sender}"}""",
            "TEST", message, "Pixel"
        ))
        assertEquals(message, output.getJSONArray("embeds").getJSONObject(0).getString("description"))
        assertEquals("TEST", output.getString("sender"))
    }

    @Test(expected = org.json.JSONException::class)
    fun rejectsInvalidJsonRatherThanSendingBrokenPayload() {
        WebhookPayload.renderTemplate("not JSON", "TEST", "body", "Pixel")
    }
}
