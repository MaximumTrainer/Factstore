package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.Notification
import com.factstore.core.domain.NotificationSeverity
import com.factstore.core.port.outbound.INotificationRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface NotificationRepositoryJpa : JpaRepository<Notification, UUID> {
    fun findAllByIsRead(isRead: Boolean): List<Notification>
    fun findAllBySeverity(severity: NotificationSeverity): List<Notification>
    fun findAllByIsReadAndSeverity(isRead: Boolean, severity: NotificationSeverity): List<Notification>
    fun countByIsReadFalse(): Long

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.isRead = false")
    fun markAllAsRead(): Int
}

@Component
class NotificationRepositoryAdapter(private val jpa: NotificationRepositoryJpa) : INotificationRepository {
    override fun save(notification: Notification): Notification = jpa.save(notification)
    override fun saveAll(notifications: List<Notification>): List<Notification> = jpa.saveAll(notifications)
    override fun findById(id: UUID): Notification? = jpa.findById(id).orElse(null)

    override fun findAll(isRead: Boolean?, severity: NotificationSeverity?): List<Notification> =
        when {
            isRead != null && severity != null -> jpa.findAllByIsReadAndSeverity(isRead, severity)
            isRead != null -> jpa.findAllByIsRead(isRead)
            severity != null -> jpa.findAllBySeverity(severity)
            else -> jpa.findAll()
        }

    override fun countUnread(): Long = jpa.countByIsReadFalse()

    override fun markAllAsRead() {
        jpa.markAllAsRead()
    }
}
