package com.example.santiagoface

import com.example.santiagoface.speech.NetworkFallbackPolicy
import com.example.santiagoface.speech.NetworkFallbackPolicy.FailureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkFallbackPolicyTest {

    @Test
    fun networkErrors_retryOnceOnline_thenSwitchToOfflineOnSecondFailure() {
        var nowMs = 1_000L
        val policy = NetworkFallbackPolicy(
            maxOnlineAttemptsBeforeFallback = 2,
            fallbackRetryOnlineIntervalMs = 120_000L,
            clockMs = { nowMs }
        )

        assertFalse(policy.useOfflineFallback)
        assertEquals(FailureAction.RETRY_ONLINE, policy.onError(isNetworkError = true))
        assertFalse(policy.useOfflineFallback)

        assertEquals(FailureAction.SWITCH_TO_OFFLINE, policy.onError(isNetworkError = true))
        assertTrue(policy.useOfflineFallback)

        // Subsequent errors while already in offline fallback should be reported normally
        assertEquals(FailureAction.REPORT_ERROR, policy.onError(isNetworkError = true))
    }

    @Test
    fun nonNetworkError_reportsImmediatelyWithoutSwitchingOffline() {
        val policy = NetworkFallbackPolicy()

        assertEquals(FailureAction.REPORT_ERROR, policy.onError(isNetworkError = false))
        assertFalse(policy.useOfflineFallback)
    }

    @Test
    fun onSuccess_resetsTransientFailureCount() {
        val policy = NetworkFallbackPolicy()

        assertEquals(FailureAction.RETRY_ONLINE, policy.onError(isNetworkError = true))
        policy.onSuccess()

        assertEquals(FailureAction.RETRY_ONLINE, policy.onError(isNetworkError = true))
        assertFalse(policy.useOfflineFallback)
    }

    @Test
    fun refreshForNewOperation_resetsFallbackAfterCooldown() {
        var nowMs = 10_000L
        val policy = NetworkFallbackPolicy(
            maxOnlineAttemptsBeforeFallback = 2,
            fallbackRetryOnlineIntervalMs = 60_000L,
            clockMs = { nowMs }
        )

        assertEquals(FailureAction.RETRY_ONLINE, policy.onError(isNetworkError = true))
        assertEquals(FailureAction.SWITCH_TO_OFFLINE, policy.onError(isNetworkError = true))
        assertTrue(policy.useOfflineFallback)

        nowMs += 30_000L
        policy.refreshForNewOperation()
        assertTrue(policy.useOfflineFallback)

        nowMs += 30_000L
        policy.refreshForNewOperation()
        assertFalse(policy.useOfflineFallback)
    }
}
