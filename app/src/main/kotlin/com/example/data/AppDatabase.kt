package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [SmsLog::class, ForwardingRule::class, AppConfig::class, Receipt::class, Outbox::class, SmsPart::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_forwarding_rules_isActive_type ON forwarding_rules (isActive, type)")
            }
        }

        fun migration2to3(protect: (String) -> String) = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS app_config (id INTEGER NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS receipts (id TEXT NOT NULL, eventKey TEXT NOT NULL, bodyHash TEXT NOT NULL, source TEXT NOT NULL, receivedAt INTEGER NOT NULL, payload TEXT NOT NULL, processed INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE UNIQUE INDEX index_receipts_eventKey ON receipts(eventKey)")
                db.execSQL("CREATE INDEX index_receipts_bodyHash_receivedAt ON receipts(bodyHash, receivedAt)")
                db.execSQL("CREATE INDEX index_receipts_processed ON receipts(processed)")
                db.execSQL("CREATE TABLE IF NOT EXISTS outbox (id TEXT NOT NULL, receiptId TEXT NOT NULL, type TEXT NOT NULL, payload TEXT NOT NULL, timestamp INTEGER NOT NULL, status TEXT NOT NULL, isTest INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX index_outbox_status ON outbox(status)")
                db.execSQL("CREATE INDEX index_outbox_receiptId ON outbox(receiptId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sms_parts (outboxId TEXT NOT NULL, part INTEGER NOT NULL, total INTEGER NOT NULL, sent INTEGER NOT NULL, delivered INTEGER NOT NULL, PRIMARY KEY(outboxId, part))")
                db.query("SELECT id, sender, message, target, ruleName, status FROM sms_logs").use { cursor ->
                    while (cursor.moveToNext()) db.execSQL("UPDATE sms_logs SET sender = ?, message = ?, target = ?, ruleName = ?, status = ? WHERE id = ?",
                        arrayOf(protect(cursor.getString(1)), protect(cursor.getString(2)), protect(cursor.getString(3)), protect(cursor.getString(4)), protect(cursor.getString(5)), cursor.getInt(0)))
                }
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE outbox ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE outbox ADD COLUMN attemptedAt INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_forwarder_database"
                )
                .addMigrations(MIGRATION_1_2, migration2to3(LocalVault(context)::encrypt), MIGRATION_3_4)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val rules = DefaultConfig.load(context).getJSONArray("rules")
                        for (index in 0 until rules.length()) {
                            val rule = rules.getJSONObject(index)
                            db.execSQL(
                                "INSERT INTO forwarding_rules (name, type, target, keywordFilter, isActive) VALUES (?, ?, ?, ?, ?)",
                                arrayOf(rule.getString("name"), rule.getString("type"), rule.getString("target"),
                                    rule.optString("keywordFilter", ""), if (rule.optBoolean("isActive", true)) 1 else 0)
                            )
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
