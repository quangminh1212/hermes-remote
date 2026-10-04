package com.hermes.bridge.protocol

import com.google.gson.JsonObject

/** Parsed representation of an inbound `command.execute` frame. */
data class CommandRequest(
    val requestId: String,
    val action: String,
    val params: JsonObject,
    val timeoutMs: Long,
) {
    fun double(key: String, default: Double = 0.0): Double =
        params.get(key)?.takeIf { !it.isJsonNull }?.asDouble ?: default

    fun int(key: String, default: Int): Int =
        params.get(key)?.takeIf { !it.isJsonNull }?.asInt ?: default

    fun string(key: String, default: String = ""): String =
        params.get(key)?.takeIf { !it.isJsonNull }?.asString ?: default
}

/** Result of executing a command on the device. */
data class CommandResult(
    val success: Boolean,
    val payload: Map<String, Any?>,
    val error: String? = null,
)
