package com.sparrowdesk.sdk

/**
 * Who sent an inbound chat message.
 */
enum class MessageSenderType {
    AGENT,
    BOT,
    SYSTEM,
    UNKNOWN;

    companion object {
        fun fromString(value: String): MessageSenderType = when (value.lowercase()) {
            "agent" -> AGENT
            "bot" -> BOT
            "system" -> SYSTEM
            else -> UNKNOWN
        }
    }
}

/**
 * An inbound chat message (from an agent, bot or the system), delivered through
 * [SparrowDeskSDK.onMessage]. Messages sent by the end user are not delivered.
 *
 * @param chatId     ID of the chat the message belongs to
 * @param messageId  ID of the message
 * @param senderType Who sent the message
 * @param text       Message text; empty when the message has no text (for example an attachment)
 * @param createdAt  Creation timestamp as reported by the widget, or null if absent
 */
data class SparrowDeskMessage(
    val chatId: String,
    val messageId: String,
    val senderType: MessageSenderType,
    val text: String,
    val createdAt: String?
)
