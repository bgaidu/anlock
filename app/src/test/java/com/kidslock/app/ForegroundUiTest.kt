package com.kidslock.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundUiTest {

    @Test
    fun visibleWhileAtLeastOneActivityIsStarted() {
        try {
            assertFalse(ForegroundUi.isVisible)
            ForegroundUi.onActivityStarted()
            assertTrue(ForegroundUi.isVisible)
            ForegroundUi.onActivityStarted()
            assertTrue(ForegroundUi.isVisible)
            ForegroundUi.onActivityStopped()
            assertTrue(ForegroundUi.isVisible)
            ForegroundUi.onActivityStopped()
            assertFalse(ForegroundUi.isVisible)
        } finally {
            while (ForegroundUi.isVisible) ForegroundUi.onActivityStopped()
        }
    }

    @Test
    fun stoppedWithoutStartedDoesNotGoNegative() {
        try {
            ForegroundUi.onActivityStopped()
            assertFalse(ForegroundUi.isVisible)
            ForegroundUi.onActivityStarted()
            assertTrue(ForegroundUi.isVisible)
        } finally {
            while (ForegroundUi.isVisible) ForegroundUi.onActivityStopped()
        }
    }
}
