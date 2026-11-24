package io.github.ravindren_sm.openstream.provider

/** Anything that must be passed before a request goes out (count caps, pacing). */
interface RequestGate {
    /** Blocks until a request may start. Throws if the gate refuses (for example a spent budget). */
    fun acquire()
}

/**
 * Pacing only, for the app: at least [minIntervalMillis] between request starts, no count cap
 * (the probe's [RequestBudget] stays strict and is not used by the app). Keeps the app polite to
 * the provider while browsing; no retries are added by this class.
 */
class RequestPacer(
    val minIntervalMillis: Long = DEFAULT_APP_INTERVAL_MILLIS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val sleepMillis: (Long) -> Unit = Thread::sleep,
) : RequestGate {
    init { require(minIntervalMillis >= 0) { "minIntervalMillis must be >= 0" } }

    private var lastStart: Long? = null

    @Synchronized
    override fun acquire() {
        lastStart?.let { last ->
            val wait = last + minIntervalMillis - nowMillis()
            if (wait > 0) sleepMillis(wait)
        }
        lastStart = nowMillis()
    }

    companion object {
        /** About 3 requests per second while browsing: much gentler than a burst, still responsive. */
        const val DEFAULT_APP_INTERVAL_MILLIS = 350L
    }
}

/**
 * Hard limits on provider traffic for one probe run (docs/private/PHASE2_PROBE_PLAN.md, section 3).
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
) : RequestGate {
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
    override fun acquire() {
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
