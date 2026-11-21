package com.example.firetviptv.provider

/**
 * Hard limits on provider traffic for one probe run (docs/PHASE2_PROBE_PLAN.md, section 3).
 *
 * - At most [maxRequests] requests per run.
 * - At least [minIntervalMillis] between the start of consecutive requests.
 * - No retries: callers must not loop on failure; one failure ends the run.
 *
 * Every network call must go through [acquire] first. The clock and sleep functions
 * are injectable so tests can check pacing without waiting.
 */
class RequestBudget(
    val maxRequests: Int = DEFAULT_MAX_REQUESTS,
    val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val sleepMillis: (Long) -> Unit = Thread::sleep,
) {
    init {
        require(maxRequests in 1..DEFAULT_MAX_REQUESTS) { "maxRequests must be 1..$DEFAULT_MAX_REQUESTS" }
        require(minIntervalMillis >= DEFAULT_MIN_INTERVAL_MILLIS) { "minIntervalMillis must be >= $DEFAULT_MIN_INTERVAL_MILLIS" }
    }

    var used: Int = 0
        private set

    private var lastStart: Long? = null

    val remaining: Int get() = maxRequests - used

    /** Blocks until the pacing interval has passed, then counts one request. */
    @Synchronized
    fun acquire() {
        if (used >= maxRequests) throw BudgetExhaustedException(maxRequests)
        lastStart?.let { last ->
            val wait = last + minIntervalMillis - nowMillis()
            if (wait > 0) sleepMillis(wait)
        }
        lastStart = nowMillis()
        used++
    }

    companion object {
        /** Plan default. The cap can be lowered for a run, never raised. */
        const val DEFAULT_MAX_REQUESTS = 10
        const val DEFAULT_MIN_INTERVAL_MILLIS = 2_000L
    }
}

class BudgetExhaustedException(max: Int) :
    IllegalStateException("Request budget of $max for this run is used up. The run stops here.")
