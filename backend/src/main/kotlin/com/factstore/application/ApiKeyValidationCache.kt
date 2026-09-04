package com.factstore.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A short-lived cache of successful key validations (#155 FR-9.2).
 *
 * `validateApiKey` runs a BCrypt comparison on every authenticated request, which at the
 * default cost factor is tens of milliseconds of CPU *per call* — for a CI pipeline posting
 * attestations in a loop, that is the dominant cost of the request.
 *
 * The cache is keyed on a SHA-256 of the presented credential, never the credential itself, so
 * a heap dump does not hand over working keys. Entries live for a configurable TTL (default
 * 60s), which bounds how long a revocation can go unnoticed — and [invalidate] closes that
 * window immediately for a revocation we perform ourselves (FR-6.4).
 */
@Component
class ApiKeyValidationCache(
    @Value("\${security.api-key.cache-ttl-seconds:60}") private val ttlSeconds: Long
) {

    private val log = LoggerFactory.getLogger(ApiKeyValidationCache::class.java)
    private val entries = ConcurrentHashMap<String, Entry>()

    private data class Entry(val keyId: UUID, val expiresAt: Instant)

    /** The cached key id for this credential, if the entry is still fresh. */
    fun get(rawKey: String): UUID? {
        if (ttlSeconds <= 0) return null
        val digest = digestOf(rawKey)
        val entry = entries[digest] ?: return null
        if (Instant.now().isAfter(entry.expiresAt)) {
            entries.remove(digest)
            return null
        }
        return entry.keyId
    }

    fun put(rawKey: String, keyId: UUID) {
        if (ttlSeconds <= 0) return
        purgeIfLarge()
        entries[digestOf(rawKey)] = Entry(keyId, Instant.now().plusSeconds(ttlSeconds))
    }

    /**
     * Drops every entry for a key. Called on revocation and rotation so those take effect on
     * the next request rather than after the TTL.
     */
    fun invalidate(keyId: UUID) {
        val removed = entries.entries.removeIf { it.value.keyId == keyId }
        if (removed) log.debug("Invalidated cached validations for key $keyId")
    }

    fun clear() = entries.clear()

    val size: Int get() = entries.size

    /** Bounded so a flood of invalid-but-distinct credentials cannot grow it without limit. */
    private fun purgeIfLarge() {
        if (entries.size < MAX_ENTRIES) return
        val now = Instant.now()
        entries.entries.removeIf { now.isAfter(it.value.expiresAt) }
        if (entries.size >= MAX_ENTRIES) entries.clear()
    }

    private fun digestOf(rawKey: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(rawKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_ENTRIES = 10_000
    }
}
