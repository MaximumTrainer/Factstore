package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class OrgType { PERSONAL, SHARED }

@Entity
@Table(name = "organisations")
class Organisation(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(unique = true, nullable = false)
    var slug: String,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: OrgType = OrgType.SHARED,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
