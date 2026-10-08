package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DatabaseMigrationTest {
    @Test fun migrationPreservesExistingRulesAndMessageLogs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-regression.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE forwarding_rules (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, target TEXT NOT NULL, keywordFilter TEXT NOT NULL, isActive INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE sms_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sender TEXT NOT NULL, message TEXT NOT NULL, timestamp INTEGER NOT NULL, ruleName TEXT NOT NULL, target TEXT NOT NULL, status TEXT NOT NULL)")
            db.execSQL("INSERT INTO forwarding_rules VALUES (1, 'Existing', 'WEBHOOK', 'https://example.com', '', 1)")
            db.execSQL("INSERT INTO sms_logs VALUES (1, 'TEST', 'Existing log', 1, 'Existing', 'https://example.com', 'SUCCESS')")
            db.version = 1
        }
        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.migration2to3 { "protected:$it" }, AppDatabase.MIGRATION_3_4).allowMainThreadQueries().build()
        try {
            room.openHelper.writableDatabase.query("SELECT name FROM forwarding_rules").use {
                assertTrue(it.moveToFirst()); assertEquals("Existing", it.getString(0)); assertEquals(1, it.count)
            }
            room.openHelper.writableDatabase.query("SELECT message FROM sms_logs").use {
                assertTrue(it.moveToFirst()); assertEquals("protected:Existing log", it.getString(0))
            }
            room.openHelper.writableDatabase.query("PRAGMA index_list(forwarding_rules)").use {
                assertTrue(it.moveToFirst()); assertEquals("index_forwarding_rules_isActive_type", it.getString(1))
            }
        } finally { room.close(); context.deleteDatabase(name) }
    }
}
