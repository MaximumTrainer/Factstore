package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "jira_configs")
class JiraConfig(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "jira_base_url", nullable = false)
    var jiraBaseUrl: String,

    @Column(name = "jira_username", nullable = false)
    var jiraUsername: String,

    @Column(name = "jira_api_token", nullable = false)
    var jiraApiToken: String,

    @Column(name = "default_project_key", nullable = false)
    var defaultProjectKey: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
