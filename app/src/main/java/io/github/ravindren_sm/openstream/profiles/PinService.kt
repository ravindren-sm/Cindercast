package io.github.ravindren_sm.openstream.profiles

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** The stored PIN check: a salted hash and the failed-attempt state. The PIN itself is never stored. */
class PinRecord(
    val algorithm: String,
    val iterations: Int,
    val salt: ByteArray,
    val hash: ByteArray,
    val failures: Int,
    val lockedUntil: Long,
)

/** Where the PIN record lives (the database in the app, a fake in tests). */
interface PinStore {
    suspend fun load(): PinRecord?
    suspend fun save(record: PinRecord)
}

sealed interface PinResult {
    data object Ok : PinResult
    /** A wrong PIN; [attemptsLeft] more guesses before a lock starts. */
    class Wrong(val attemptsLeft: Int) : PinResult
    /** Too many wrong guesses: nothing is checked until [untilMillis]. */
    class Locked(val untilMillis: Long) : PinResult
    data object NotSet : PinResult
}

/**
 * The one household PIN: four digits, kept only as a salted PBKDF2 hash. Five wrong guesses in a row start a lock
 * (30 s, doubling with each further wrong guess, capped at 15 minutes). A four-digit PIN is a family speed bump, not
 * strong security: someone who can read the app's private storage can try all 10,000 values.
 */
class PinService(
    private val store: PinStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val iterations: Int = 20_000,
    private val random: SecureRandom = SecureRandom(),
) {
    suspend fun isSet(): Boolean = store.load() != null

    /** Sets (or replaces) the PIN. Throws if it is not exactly four digits. */
    suspend fun set(pin: String) {
        require(isValid(pin)) { "A PIN is exactly four digits" }
        val salt = ByteArray(16).also(random::nextBytes)
        val (algorithm, hash) = hash(pin, salt, iterations)
        store.save(PinRecord(algorithm, iterations, salt, hash, failures = 0, lockedUntil = 0))
    }

    suspend fun verify(pin: String): PinResult {
        val record = store.load() ?: return PinResult.NotSet
        val now = nowMillis()
        if (record.lockedUntil > now) return PinResult.Locked(record.lockedUntil)
        val candidate = runCatching { hashWith(record.algorithm, pin, record.salt, record.iterations) }.getOrNull()
        if (candidate != null && isValid(pin) && MessageDigest.isEqual(candidate, record.hash)) {
            if (record.failures != 0 || record.lockedUntil != 0L) store.save(record.copyWith(failures = 0, lockedUntil = 0))
            return PinResult.Ok
        }
        val failures = record.failures + 1
        val lock = if (failures >= MAX_FREE_ATTEMPTS) now + lockMillis(failures) else 0L
        store.save(record.copyWith(failures = failures, lockedUntil = lock))
        return if (lock > 0) PinResult.Locked(lock) else PinResult.Wrong(MAX_FREE_ATTEMPTS - failures)
    }

    /** Replaces the PIN if [old] is right. */
    suspend fun change(old: String, new: String): PinResult {
        val verified = verify(old)
        if (verified is PinResult.Ok) set(new)
        return verified
    }

    private fun PinRecord.copyWith(failures: Int, lockedUntil: Long) = PinRecord(algorithm, iterations, salt, hash, failures, lockedUntil)

    private fun hash(pin: String, salt: ByteArray, iterations: Int): Pair<String, ByteArray> {
        for (algorithm in ALGORITHMS) {
            try { return algorithm to hashWith(algorithm, pin, salt, iterations) } catch (_: java.security.NoSuchAlgorithmException) { /* older Android: try the next */ }
        }
        error("No PBKDF2 implementation available")
    }

    private fun hashWith(algorithm: String, pin: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance(algorithm).generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, 256)).encoded

    companion object {
        const val MAX_FREE_ATTEMPTS = 5
        private const val BASE_LOCK_MILLIS = 30_000L
        private const val MAX_LOCK_MILLIS = 15 * 60_000L
        private val ALGORITHMS = listOf("PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA1")

        fun isValid(pin: String) = pin.length == 4 && pin.all { it in '0'..'9' }

        /** 5th wrong guess: 30 s; each further one doubles it, up to 15 minutes. */
        fun lockMillis(failures: Int): Long {
            val doublings = (failures - MAX_FREE_ATTEMPTS).coerceIn(0, 10)
            return minOf(BASE_LOCK_MILLIS shl doublings, MAX_LOCK_MILLIS)
        }
    }
}
