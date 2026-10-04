package com.hermes.bridge.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.hermes.bridge.model.ChatMessage
import com.hermes.bridge.model.Role
import com.hermes.bridge.model.ServerConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A single item emitted while streaming a completion. */
sealed interface StreamEvent {
    /** A piece of assistant text. */
    data class Delta(val text: String) : StreamEvent
    /** The stream finished successfully. */
    data class Completed(val fullText: String) : StreamEvent
    /** Something went wrong (HTTP error, network, etc.). */
    data class Failed(val message: String) : StreamEvent
}

/**
 * Talks to a Hermes API server's Claude-Fable-compatible `/v1/chat/completions`
 * endpoint and streams the assistant reply back chunk by chunk.
 *
 * Blocking by design: callers invoke [streamChat] on a background dispatcher.
 */
class ChatApi(
    private val client: OkHttpClient = defaultClient(),
) {

    /**
     * Send the conversation and stream back the reply.
     *
     * @param onEvent invoked for each [StreamEvent] as it arrives.
     * @return the accumulated assistant text (also sent as [StreamEvent.Completed]).
     */
    fun streamChat(
        config: ServerConfig,
        history: List<ChatMessage>,
        onEvent: (StreamEvent) -> Unit,
    ): String {
        if (!config.isValid()) {
            onEvent(StreamEvent.Failed("Chưa cấu hình URL hoặc API key"))
            return ""
        }

        val body = buildRequestBody(config, history)
        val request = Request.Builder()
            .url(config.chatEndpoint)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        val full = StringBuilder()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    val msg = describeHttpError(response, err)
                    onEvent(StreamEvent.Failed(msg))
                    return full.toString()
                }
                val source = response.body?.source()
                    ?: run {
                        onEvent(StreamEvent.Failed("Phản hồi rỗng từ server"))
                        return full.toString()
                    }
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    val trimmed = line.trimEnd('\r')
                    if (trimmed.isEmpty()) continue
                    if (!trimmed.startsWith("data:")) continue
                    val payload = trimmed.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue
                    if (payload == "[DONE]") break
                    SseParser.extractDeltas(payload).forEach { delta ->
                        full.append(delta)
                        onEvent(StreamEvent.Delta(delta))
                    }
                }
            }
        } catch (e: IOException) {
            onEvent(StreamEvent.Failed("Lỗi kết nối: ${e.message ?: e.javaClass.simpleName}"))
            return full.toString()
        }

        onEvent(StreamEvent.Completed(full.toString()))
        return full.toString()
    }

    /** Fetch the list of model ids the server exposes. Blocking. */
    fun listModels(config: ServerConfig): List<String> {
        val request = Request.Builder()
            .url(config.modelsEndpoint)
            .header("Authorization", "Bearer ${config.apiKey}")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val json = response.body?.string().orEmpty()
                parseModelIds(json)
            }
        } catch (e: IOException) {
            emptyList()
        }
    }

    private fun buildRequestBody(config: ServerConfig, history: List<ChatMessage>): String {
        val messages = JsonArray()
        history
            .filter { it.state != com.hermes.bridge.model.DeliveryState.ERROR }
            .filter { it.content.isNotBlank() }
            .forEach { msg ->
                val obj = JsonObject()
                obj.addProperty("role", msg.role.wire)
                obj.addProperty("content", msg.content)
                messages.add(obj)
            }

        val root = JsonObject()
        root.addProperty("model", config.model.ifBlank { ServerConfig.DEFAULT_MODEL })
        root.add("messages", messages)
        root.addProperty("stream", true)
        return root.toString()
    }

    private fun describeHttpError(response: Response, body: String): String {
        val detail = SseParser.readStringField(body, "message")
            ?: body.take(200).ifBlank { "không có nội dung" }
        return "HTTP ${response.code}: $detail"
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** Parse `{"data":[{"id":"..."}]}` or a bare `["id", ...]` list. */
        fun parseModelIds(json: String): List<String> {
            val ids = mutableListOf<String>()
            val dataIdx = json.indexOf("\"data\"")
            val scanFrom = if (dataIdx >= 0) dataIdx else 0
            var from = scanFrom
            while (true) {
                val at = json.indexOf("\"id\"", from)
                if (at < 0) break
                val colon = json.indexOf(':', at + 4)
                if (colon < 0) break
                val value = readJsonStringValue(json, colon + 1)
                if (value != null && value.isNotBlank()) ids += value
                from = colon + 1
            }
            return ids.distinct()
        }

        private fun readJsonStringValue(json: String, start: Int): String? {
            var i = start
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length || json[i] != '"') return null
            i++
            val sb = StringBuilder()
            while (i < json.length && json[i] != '"') {
                if (json[i] == '\\' && i + 1 < json.length) {
                    i++
                    when (val esc = json[i]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(json[i])
                }
                i++
            }
            return sb.toString()
        }

        /**
         * Read timeout is disabled because a streamed completion can idle while
         * the model thinks between tokens.
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
