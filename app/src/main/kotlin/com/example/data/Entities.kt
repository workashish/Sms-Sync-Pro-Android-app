package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_logs")
data class SmsLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val ruleName: String,
    val target: String,
    val status: String // "SUCCESS", "FAILED"
)

@Entity(
    tableName = "forwarding_rules",
    indices = [androidx.room.Index(value = ["isActive", "type"])]
)
data class ForwardingRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // "SMS" or "WEBHOOK"
    val target: String, // Phone number or Webhook URL
    val keywordFilter: String = "", // Empty means all
    val isActive: Boolean = true
)

@Entity(tableName = "app_config")
data class AppConfig(@PrimaryKey val id: Int = 1, val payload: String)

@Entity(tableName = "receipts", indices = [androidx.room.Index(value = ["eventKey"], unique = true), androidx.room.Index(value = ["bodyHash", "receivedAt"]), androidx.room.Index(value = ["processed"])])
data class Receipt(@PrimaryKey val id: String, val eventKey: String, val bodyHash: String, val source: String, val receivedAt: Long, val payload: String, val processed: Boolean = false)

@Entity(tableName = "outbox", indices = [androidx.room.Index(value = ["status"]), androidx.room.Index(value = ["receiptId"])])
data class Outbox(@PrimaryKey val id: String, val receiptId: String, val type: String, val payload: String, val timestamp: Long, val status: String = "PENDING", val isTest: Boolean = false, @androidx.room.ColumnInfo(defaultValue = "0") val attempts: Int = 0, @androidx.room.ColumnInfo(defaultValue = "0") val attemptedAt: Long = 0)

@Entity(tableName = "sms_parts", primaryKeys = ["outboxId", "part"])
data class SmsPart(val outboxId: String, val part: Int, val total: Int, val sent: Int = 0, val delivered: Int = 0)
