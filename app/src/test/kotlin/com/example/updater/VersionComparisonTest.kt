package com.example.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparisonTest {
    @Test fun detectsNewerVersionsWithoutOfferingDowngrades() {
        assertTrue(VersionComparison.isNewer("2.10", "2.9"))
        assertFalse(VersionComparison.isNewer("1.9", "2.0"))
        assertFalse(VersionComparison.isNewer("2.0.0", "2.0"))
        assertFalse(VersionComparison.isNewer("bad", "2.0"))
    }
}
