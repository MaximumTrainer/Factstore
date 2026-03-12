package com.factstore.adapter.outbound

import com.factstore.core.port.outbound.ISlackNotificationSender
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class HttpSlackNotificationSender : ISlackNotificationSender {

    private val log = LoggerFactory.getLogger(HttpSlackNotificationSender::class.java)
    private val restTemplate = RestTemplate()

    override fun send(botToken: String, channel: String, message: String): Boolean {
        return try {
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("Authorization", "Bearer $botToken")
            }
            val body = mapOf("channel" to channel, "text" to message)
            val entity = HttpEntity(body, headers)
            restTemplate.postForEntity("https://slack.com/api/chat.postMessage", entity, Map::class.java)
            log.info("Sent Slack message to channel: $channel")
            true
        } catch (e: Exception) {
            log.error("Failed to send Slack message to channel $channel: ${e.message}", e)
            false
        }
    }
}
