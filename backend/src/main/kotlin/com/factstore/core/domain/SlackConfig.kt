package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "slack_configs")
class SlackConfig(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "org_slug", nullable = false, unique = true)
    val orgSlug: String,

    @Column(name = "bot_token", nullable = false)
    var botToken: String,

    @Column(name = "signing_secret", nullable = false)
    var signingSecret: String,

    @Column(name = "channel", nullable = false)
    var channel: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
