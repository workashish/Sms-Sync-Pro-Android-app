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

@Entity(tableName = "forwarding_rules")
data class ForwardingRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // "SMS" or "WEBHOOK"
    val target: String, // Phone number or Webhook URL
    val keywordFilter: String = "", // Empty means all
    val isActive: Boolean = true
)
