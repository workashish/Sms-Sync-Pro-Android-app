package com.example.processor
import org.junit.Assert.*
import org.junit.Test
class SafeFilterTest {
    @Test(timeout = 3000) fun pathologicalBacktrackingPatternsRemainBounded() {
        assertFalse(SafeFilter.matches("/(a+)+$/", "sender", "a".repeat(100000) + "!"))
    }
    @Test fun filtersAreCaseSensitiveUnlessRequestedAndRejectUnsupportedPatterns() {
        assertTrue(SafeFilter.matches("/otp/i", "BANK", "OTP 123456"))
        assertFalse(SafeFilter.matches("/otp/", "BANK", "OTP 123456"))
        assertFalse(SafeFilter.matches("/(?<=otp)123/", "BANK", "otp123"))
        assertTrue(SafeFilter.matches("bank", "BANK", "hello"))
    }
}
