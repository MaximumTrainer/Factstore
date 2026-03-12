package com.factstore.core.port.outbound

interface ISlackNotificationSender {
    /**
     * Sends a text message to a Slack channel using the provided bot token.
     *
     * @param botToken   Slack bot OAuth token (xoxb-...)
     * @param channel    Slack channel name or ID (e.g. "#deployments")
     * @param message    Plain text or mrkdwn-formatted message body
     * @return true if the message was delivered successfully, false otherwise
     */
    fun send(botToken: String, channel: String, message: String): Boolean
}
