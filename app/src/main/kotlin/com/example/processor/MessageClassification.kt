package com.example.processor

import org.json.JSONObject

object MessageClassification {
    data class Parsed(val type: String, val code: String? = null, val amount: String? = null, val currency: String? = null, val bankType: String? = null)
    fun parse(body: String): Parsed {
        val after = Regex("\\b(?:otp|one[ -]time password|verification code|security code|authentication code|passcode|code|pin)\\b[^\\d\\n]{0,40}\\b(\\d{4,8})\\b", RegexOption.IGNORE_CASE).find(body)
        val before = Regex("\\b(\\d{4,8})\\b\\s*(?:is\\s+)?(?:your\\s+|the\\s+)?(?:otp|verification code|security code|passcode)\\b", RegexOption.IGNORE_CASE).find(body)
        val google = Regex("\\bG-(\\d{6})\\b").find(body)
        val code = after?.groupValues?.get(1) ?: before?.groupValues?.get(1) ?: google?.groupValues?.get(1)
        if (code != null || Regex("\\b(?:otp|one[ -]time password|verification code|security code|authentication code|passcode)\\b", RegexOption.IGNORE_CASE).containsMatchIn(body)) return Parsed("otp", code)
        if (!Regex("\\b(?:debited|credited|transaction|withdrawn|deposited|payment|bank|card ending)\\b|account balance", RegexOption.IGNORE_CASE).containsMatchIn(body)) return Parsed("message")
        val kind = if (Regex("\\b(?:credited|deposited|deposit)\\b", RegexOption.IGNORE_CASE).containsMatchIn(body)) "DEPOSIT"
            else if (Regex("\\b(?:debited|paid|payment|withdrawn)\\b", RegexOption.IGNORE_CASE).containsMatchIn(body)) "PAYMENT" else "OTHER"
        val actions = Regex("\\b(?:debited|credited|paid|payment|withdrawn|deposited|transaction|amount)\\b", RegexOption.IGNORE_CASE).findAll(body).map { it.range.first }.toList()
        val match = Regex("(₹|Rs\\.?|INR|USD|\\$|EUR|€|GBP|£)\\s*([\\d]+(?:,[\\d]{2,3})*(?:\\.[\\d]{1,2})?)\\b", RegexOption.IGNORE_CASE).findAll(body).minByOrNull { candidate ->
            val nearest = actions.minOfOrNull { kotlin.math.abs(it - candidate.range.first) } ?: 1000
            nearest + if (Regex("balance|limit", RegexOption.IGNORE_CASE).containsMatchIn(body.substring((candidate.range.first - 24).coerceAtLeast(0), candidate.range.first))) 1000 else 0
        }
        val currency = match?.groupValues?.get(1)?.let { value -> when {
            Regex("₹|Rs\\.?|INR", RegexOption.IGNORE_CASE).matches(value) -> "INR"
            Regex("USD|\\$", RegexOption.IGNORE_CASE).matches(value) -> "USD"
            Regex("EUR|€", RegexOption.IGNORE_CASE).matches(value) -> "EUR"
            else -> "GBP"
        } }
        return Parsed("bank", amount = match?.groupValues?.get(2)?.replace(",", ""), currency = currency, bankType = kind)
    }
    fun metadata(parsed: Parsed, encrypted: Boolean): JSONObject = JSONObject().apply {
        parsed.bankType?.let { put("bank_type", it) }
        if (!encrypted) {
            parsed.code?.let { put("code", it) }; parsed.amount?.let { put("amount", it) }; parsed.currency?.let { put("currency", it) }
        }
    }
}
