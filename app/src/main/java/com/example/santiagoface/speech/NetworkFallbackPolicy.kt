package com.example.santiagoface.speech

/**
 * Tracks network failures for speech services and coordinates retrying online once
 * before falling back to an on-device/offline alternative.
 */
class NetworkFallbackPolicy(
    private val maxOnlineAttemptsBeforeFallback: Int = 2,
    private val fallbackRetryOnlineIntervalMs: Long = 120_000L,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    enum class FailureAction {
        RETRY_ONLINE,
        SWITCH_TO_OFFLINE,
        REPORT_ERROR
    }

    var useOfflineFallback: Boolean = false
        private set

    private var networkFailureCount: Int = 0
    private var fallbackActivatedAtMs: Long = 0L

    /**
     * Resets offline fallback if the cooldown has elapsed so online features can be probed again.
     */
    fun refreshForNewOperation() {
        if (useOfflineFallback && clockMs() - fallbackActivatedAtMs >= fallbackRetryOnlineIntervalMs) {
            reset()
        }
    }

    /**
     * Records a successful operation, clearing transient online failure counts.
     */
    fun onSuccess() {
        if (!useOfflineFallback) {
            networkFailureCount = 0
        }
    }

    /**
     * Handles an operation error and returns whether to retry online, switch to offline, or report the error.
     */
    fun onError(isNetworkError: Boolean): FailureAction {
        if (!isNetworkError || useOfflineFallback) {
            return FailureAction.REPORT_ERROR
        }

        networkFailureCount++
        return if (networkFailureCount < maxOnlineAttemptsBeforeFallback) {
            FailureAction.RETRY_ONLINE
        } else {
            useOfflineFallback = true
            fallbackActivatedAtMs = clockMs()
            FailureAction.SWITCH_TO_OFFLINE
        }
    }

    /**
     * Resets the policy back to online-first mode.
     */
    fun reset() {
        networkFailureCount = 0
        useOfflineFallback = false
        fallbackActivatedAtMs = 0L
    }
}
