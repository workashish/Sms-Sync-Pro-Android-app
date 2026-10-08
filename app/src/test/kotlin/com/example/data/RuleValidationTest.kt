package com.example.data

import org.junit.Assert.*
import org.junit.Test

class RuleValidationTest {
    @Test fun rejectsMalformedDestinationsAndRegexWithoutBlockingValidRules() {
        assertNotNull(RuleValidation.error("WEBHOOK", "https://", "", false))
        assertNotNull(RuleValidation.error("WEBHOOK", "http://example.com", "", false))
        assertNull(RuleValidation.error("WEBHOOK", "http://10.0.2.2:4320", "", true))
        assertNull(RuleValidation.error("WEBHOOK", "https://example.com/api/webhooks", "", false))
        assertNotNull(RuleValidation.error("SMS", "abc", "", false))
        assertNull(RuleValidation.error("SMS", "+91 98765 43210", "", false))
        assertNotNull(RuleValidation.error("SMS", "12345", "/[/", false))
        assertNull(RuleValidation.error("SMS", "12345", "/otp/i", false))
    }
}
