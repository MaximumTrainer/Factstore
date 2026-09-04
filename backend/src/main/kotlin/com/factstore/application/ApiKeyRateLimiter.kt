package com.factstore.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.math.pow

/**
 * Rate-limits failed authentication, per source and per credential prefix (#155 FR-9.1).
 *
 * Without this, a stolen key prefix can be brute-forced at whatever rate the network allows,
 * and each attempt costs a BCrypt comparison — so an attacker also gets a cheap CPU denial of
 * service for free.
 *
 * Failures back off exponentially and a success clears the counter, so a pipeline that fixes
 * its credential is not left locked out. Counted separately by source and by prefix: one
 * misconfigured runner should not lock out a whole NAT, and one bad key should not be
 * rescued by rotating source addresses.
 */
@Component
class ApiKeyRateLimiter(
    @Value("\${security.api-key.rate-limit.max-failures:5}") private val maxFailures: Int,
    @Value("\${security.api-key.rate-limit.base-backoff-seconds:2}") private val baseBackoffSeconds: Long,
    @Value("\${security.api-key.rate-limit.max-backoff-seconds:300}") private val maxBackoffSeconds: Long,
    @Value("\${security.api-key.rate-limit.enabled:true}") private val enabled: Boolean
) {

    private val log = LoggerFactory.getLogger(ApiKeyRateLimiter::class.java)
    private val buckets = ConcurrentHashMap<String, Bucket>()

    private class Bucket(@Volatile var failures: Int = 0, @Volatile var blockedUntil: Instant? = null)

    fun isBlocked(sourceIp: String, credentialPrefix: String): Boolean {
        if (!enabled) return false
        val now = Instant.now()
        return keysFor(sourceIp, credentialPrefix).any { key ->
            val bucket = buckets[key] ?: return@any false
            val until = bucket.blockedUntil ?: return@any false
            if (now.isAfter(until)) {
                // The window has passed; let the next attempt through rather than resetting
                // the failure count, so a persistent attacker keeps backing off.
                bucket.blockedUntil = null
                false
            } else {
                true
            }
        }
    }

    fun retryAfterSeconds(sourceIp: String, credentialPrefix: String): Long {
        val now = Instant.now()
        return keysFor(sourceIp, credentialPrefix)
            .mapNotNull { buckets[it]?.blockedUntil }
            .filter { it.isAfter(now) }
            .maxOfOrNull { java.time.Duration.between(now, it).seconds.coerceAtLeast(1) }
            ?: 1
    }

    fun recordFailure(sourceIp: String, credentialPrefix: String) {
        if (!enabled) return
        keysFor(sourceIp, credentialPrefix).forEach { key ->
            val bucket = buckets.computeIfAbsent(key) { Bucket() }
            bucket.failures += 1
            if (bucket.failures >= maxFailures) {
                val backoff = backoffFor(bucket.failures)
                bucket.blockedUntil = Instant.now().plusSeconds(backoff)
                log.warn("Blocking authentication for '$key' for ${backoff}s after ${bucket.failures} failures")
            }
        }
        purgeIfLarge()
    }

    /** A working credential clears the counters, so a fixed pipeline is not left locked out. */
    fun recordSuccess(sourceIp: String, credentialPrefix: String) {
        keysFor(sourceIp, credentialPrefix).forEach { buckets.remove(it) }
    }

    fun reset() = buckets.clear()

    private fun backoffFor(failures: Int): Long {
        val exponent = (failures - maxFailures).coerceAtLeast(0)
        val scaled = baseBackoffSeconds * 2.0.pow(exponent.toDouble())
        return min(scaled, maxBackoffSeconds.toDouble()).toLong().coerceAtLeast(1)
    }

    private fun keysFor(sourceIp: String, credentialPrefix: String): List<String> =
        listOf("ip:$sourceIp", "key:$credentialPrefix")

    private fun purgeIfLarge() {
        if (buckets.size < MAX_BUCKETS) return
        val now = Instant.now()
        buckets.entries.removeIf { (_, bucket) ->
            bucket.blockedUntil?.let { now.isAfter(it.plusSeconds(maxBackoffSeconds)) } ?: (bucket.failures == 0)
        }
        if (buckets.size >= MAX_BUCKETS) buckets.clear()
    }

    private companion object {
        const val MAX_BUCKETS = 50_000
    }
}
