package com.example

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.data.*
import com.example.processor.MessageProcessor
import com.example.worker.WebhookWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CoreReliabilityTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val services get() = EntryPointAccessors.fromApplication(context.applicationContext, com.example.di.AppServices::class.java)

    @Test fun localEncryptionAuthenticatesAndDoesNotStorePlaintext() {
        val vault = services.vault()
        val clear = "Sensitive OTP 654321 नमस्ते"
        val encrypted = vault.encrypt(clear)
        assertTrue(encrypted.startsWith("local:v1:"))
        assertFalse(encrypted.contains("654321"))
        assertEquals(clear, vault.decrypt(encrypted))
        assertNotEquals(encrypted, vault.encrypt(clear))
    }
    @Test fun configurationImportIsAtomicAndIdempotent() = runBlocking {
        val dao = services.dao(); val settings = services.settings()
        val original = settings.snapshot()
        val name = "instrumented-import-${UUID.randomUUID()}"
        val rule = ForwardingRule(name = name, type = "WEBHOOK", target = "http://10.0.2.2:4320/api/webhooks/incoming", isActive = false)
        try {
            val invalid = """{"settings":{"webhookTimeout":15},"rules":[{"name":"bad","type":"WRONG","target":"https://example.com"}]}"""
            try { ConfigImport.parse(invalid, true); fail("Invalid import accepted") } catch (_: IllegalArgumentException) {}
            assertEquals(original.getInt("webhookTimeout"), settings.webhookTimeout.first())
            settings.applyImport(JSONObject().put("webhookTimeout",15), listOf(rule))
            settings.applyImport(JSONObject().put("webhookTimeout",15), listOf(rule))
            assertEquals(15, settings.webhookTimeout.first())
            assertEquals(1, dao.getAllRulesNonFlow().count { it.name == name })
            assertFalse(dao.getConfig()!!.payload.contains("webhookSecret"))
        } finally {
            dao.getAllRulesNonFlow().filter { it.name == name }.forEach { dao.deleteRuleById(it.id) }
            settings.applyImport(original, emptyList())
        }
    }
    @Test fun largeMessagesUseEncryptedDurableQueueAndPausedDeliveriesResumeWithSameId() = runBlocking {
        val dao=services.dao(); val settings=services.settings(); val vault=services.vault()
        val rules=dao.getAllRulesNonFlow(); val original=settings.snapshot()
        val body="Large message "+"x".repeat(20000)
        val event="instrumented-large-${UUID.randomUUID()}"
        var testRule=0
        try {
            rules.forEach { dao.setRuleActive(it.id,false) }
            dao.insertRule(ForwardingRule(name=event,type="WEBHOOK",target="http://10.0.2.2:4320/api/webhooks/incoming"))
            testRule=dao.getAllRulesNonFlow().first { it.name==event }.id
            settings.updateGlobalEnable(true)
            val receiptId = services.processor().processMessage("TEST",body,System.currentTimeMillis(),"SMS",event)!!
            val receipt = dao.receipt(receiptId)!!
            assertFalse(receipt.payload.contains("Large message"))
            services.processor().route(receipt)
            val queued=dao.pendingForReceipt(receipt.id).single()
            assertFalse(queued.payload.contains("Large message"))
            settings.updateGlobalEnable(false)
            val worker=TestListenableWorkerBuilder<WebhookWorker>(context).setWorkerFactory(services.workerFactory())
                .setInputData(workDataOf("outboxId" to queued.id)).build()
            assertEquals(androidx.work.ListenableWorker.Result.retry(),worker.doWork())
            assertEquals(0,dao.outbox(queued.id)!!.attempts)
            settings.updateGlobalEnable(true)
            val resumed=TestListenableWorkerBuilder<WebhookWorker>(context).setWorkerFactory(services.workerFactory())
                .setInputData(workDataOf("outboxId" to queued.id)).build()
            val result = resumed.doWork()
            val diagnostic = dao.getRecentLogs().first().firstOrNull()?.let { vault.reveal(it).status } ?: "No delivery log"
            assertEquals(diagnostic, androidx.work.ListenableWorker.Result.success(), result)
            assertEquals("SUCCESS",dao.outbox(queued.id)!!.status)
            val log=dao.getRecentLogs().first().first { vault.reveal(it).message==body }
            assertFalse(log.message.contains("Large message")); assertEquals(body,vault.reveal(log).message)
        } finally {
            if(testRule!=0)dao.deleteRuleById(testRule)
            rules.forEach{dao.setRuleActive(it.id,it.isActive)}
            settings.applyImport(original,emptyList())
        }
    }
    @Test fun unauthorizedCommandsNeverCreateSmsRepliesAndDuplicatesDoNotCreateNewReceipts() = runBlocking {
        val dao=services.dao();val settings=services.settings();val original=settings.snapshot();val rules=dao.getAllRulesNonFlow()
        try {
            rules.forEach { dao.setRuleActive(it.id,false) }
            settings.updateAuthorizedCommandSenders("");settings.updateGlobalEnable(true)
            val event="command-${UUID.randomUUID()}"
            val id = services.processor().processMessage("+1234567890","STATUS",System.currentTimeMillis(),"SMS",event)!!
            val receipt=dao.receipt(id)!!
            services.processor().route(receipt)
            assertTrue(dao.pendingForReceipt(receipt.id).isEmpty())
            services.processor().processMessage("+1234567890","STATUS",receipt.receivedAt,"SMS",event)
            assertTrue(dao.receipt(receipt.id)!!.processed)
        } finally {rules.forEach{dao.setRuleActive(it.id,it.isActive)};settings.applyImport(original,emptyList())}
    }
    @Test fun fixedSettingsSurviveImportsAndResetLegacyOverrides() = runBlocking {
        val dao = services.dao(); val settings = services.settings(); val vault = services.vault()
        val original = settings.snapshot()
        try {
            val override = JSONObject().put("updateUrl", "https://untrusted.example").put("webhookSecret", "changed").put("aesEncryptionKey", "changed")
            settings.applyImport(override, emptyList())
            val imported = settings.snapshot()
            FixedSettings.keys.forEach { assertEquals(original.getString(it), imported.getString(it)) }
            val legacy = JSONObject(original.toString())
            override.keys().forEach { legacy.put(it, override.get(it)) }
            dao.saveConfig(AppConfig(payload = vault.encrypt(legacy.toString())))
            val migrated = settings.snapshot()
            val persisted = JSONObject(vault.decrypt(dao.getConfig()!!.payload))
            FixedSettings.keys.forEach {
                assertEquals(original.getString(it), migrated.getString(it))
                assertEquals(original.getString(it), persisted.getString(it))
            }
            assertEquals(FixedSettings.UPDATE_URL, settings.updateUrl.first())
        } finally { settings.applyImport(original, emptyList()) }
    }
}
