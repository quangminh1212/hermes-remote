package com.hermes.bridge.model

/** Role of a message in the conversation. */
enum class Role(val wire: String) {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system");

    companion object {
        fun fromWire(value: String?): Role =
            entries.firstOrNull { it.wire == value } ?: ASSISTANT
    }
}

/** Delivery state of a message shown in the chat list. */
enum class DeliveryState { SENT, STREAMING, DONE, ERROR }

/**
 * One entry in the chat transcript.
 *
 * [content] grows while [state] is [DeliveryState.STREAMING] as chunks arrive.
 */
data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val state: DeliveryState = DeliveryState.DONE,
    val error: String? = null,
) {
    val isUser: Boolean get() = role == Role.USER
}
