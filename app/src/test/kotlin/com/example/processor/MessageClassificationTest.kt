package com.example.processor
import org.junit.Assert.*
import org.junit.Test
class MessageClassificationTest {
    @Test fun avoidsDatesAccountNumbersAndWordsContainingCode() {
        assertEquals("123456", MessageClassification.parse("On 08/10/2026 your OTP is 123456").code)
        assertEquals("message", MessageClassification.parse("Please decode the barcode on your account").type)
        assertEquals("1234.50", MessageClassification.parse("Account 9876543210 debited Rs. 1,234.50. Balance Rs. 9999").amount)
        assertEquals("INR", MessageClassification.parse("Payment of ₹500 was made").currency)
        assertEquals("DEPOSIT", MessageClassification.parse("INR 300 credited to your bank").bankType)
        assertEquals("123456", MessageClassification.parse("G-123456 is your Google verification code").code)
    }
}
