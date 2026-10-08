package com.example.updater

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class UpdateMetadataTest {
    private fun release(tag: String) = JSONObject("""{"tag_name":"$tag","assets":[{"name":"notes.txt","browser_download_url":"https://example.com/notes.txt"},{"name":"app.apk","browser_download_url":"https://example.com/app.apk","digest":"sha256:${"a".repeat(64)}"}]}""")

    @Test fun choosesApkAndHandlesSameVersionBuildUpdatesWithoutDowngrades() {
        assertEquals("https://example.com/app.apk", UpdateMetadata.parse(release("v2.1.0"), 2, "2.0")!!.downloadUrl)
        assertEquals(3, UpdateMetadata.parse(release("v2.0-build3"), 2, "2.0")!!.versionCode)
        assertNull(UpdateMetadata.parse(release("v2.0-build2"), 2, "2.0"))
        assertNull(UpdateMetadata.parse(release("v1.0-build10"), 2, "2.0"))
        assertNull(UpdateMetadata.parse(release("v2.0.0"), 2, "2.0"))
    }
    @Test fun ignoresDraftAndPrerelease() {
        assertNull(UpdateMetadata.parse(release("v3.0").put("draft", true), 2, "2.0"))
        assertNull(UpdateMetadata.parse(release("v3.0").put("prerelease", true), 2, "2.0"))
    }
    @Test fun rejectsMissingApkAndInvalidMetadata() {
        for (metadata in listOf(release("v3.0").put("assets", org.json.JSONArray()), JSONObject("{}"), release("garbage"))) {
            try { UpdateMetadata.parse(metadata, 2, "2.0"); fail("Invalid metadata was accepted") } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun customMetadataRequiresNewBuildAndSecureUrl() {
        assertNotNull(UpdateMetadata.parse(JSONObject("""{"versionCode":3,"downloadUrl":"https://example.com/update.apk","sha256":"${"a".repeat(64)}"}"""), 2, "2.0"))
        assertNull(UpdateMetadata.parse(JSONObject("""{"versionCode":1}"""), 2, "2.0"))
        for (url in listOf("http://example.com/update.apk", "https://", "https://user:password@example.com/a.apk")) {
            try { UpdateMetadata.secureUrl(url); fail("Invalid URL accepted") } catch (_: Exception) {}
        }
    }
}
