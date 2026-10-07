package io.hermes.remote.data

import io.hermes.remote.R

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.hermes.remote.model.ChatMessage
import io.hermes.remote.model.Role
import io.hermes.remote.model.ServerConfig
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
     * UI strings this class needs.
     *
     * Injected rather than read from resources so the class stays free of an
     * Android Context and its behaviour stays unit-testable on the JVM.
     */
    data class Messages(
        val noConfig: String,
        val emptyBody: String,
        val connectFailed: (String) -> String,
        val noContent: String,
    ) {
        companion object {
            /** English defaults, used by tests that do not care about wording. */
            val DEFAULT = Messages(
                noConfig = "URL or API key not configured",
                emptyBody = "Empty response from the server",
                connectFailed = { "Connection error: $it" },
                noContent = "no content",
            )

            /** Build from Android resources at the call site. */
            fun from(context: android.content.Context) = Messages(
                noConfig = context.getString(R.string.net_no_config),
                emptyBody = context.getString(R.string.net_empty_body),
                connectFailed = { context.getString(R.string.net_connect_failed, it) },
                noContent = context.getString(R.string.net_no_content),
            )
        }
    }

    /**
     * Send the conversation and stream back the reply.
     *
     * [messages] is declared before [onEvent] so callers keep using a trailing
     * lambda, which reads better than an explicitly named callback argument.
     *
     * @param onEvent invoked for each [StreamEvent] as it arrives.
     * @return the accumulated assistant text (also sent as [StreamEvent.Completed]).
     */
    fun streamChat(
        config: ServerConfig,
        history: List<ChatMessage>,
        messages: Messages = Messages.DEFAULT,
        onEvent: (StreamEvent) -> Unit,
    ): String {
        if (!config.isValid()) {
            onEvent(StreamEvent.Failed(messages.noConfig))
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
                    val msg = describeHttpError(response, err, messages)
                    onEvent(StreamEvent.Failed(msg))
                    return full.toString()
                }
                val source = response.body?.source()
                    ?: run {
                        onEvent(StreamEvent.Failed(messages.emptyBody))
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
            onEvent(StreamEvent.Failed(messages.connectFailed(e.message ?: e.javaClass.simpleName)))
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
            .filter { it.state != io.hermes.remote.model.DeliveryState.ERROR }
            .filter { it.content.isNotBlank() }
            .forEach { msg ->
                val obj = JsonObject()
                obj.addProperty("role", msg.role.wire)
                obj.addProperty("content", msg.content)
                messages.add(obj)
            }

        val root = JsonObject()
        // effectiveModel, not model: when the server reported agents, a model
        // id that is not one of them is rejected with 404 by the bridge.
        root.addProperty("model", config.effectiveModel.ifBlank { ServerConfig.DEFAULT_MODEL })
        root.add("messages", messages)
        root.addProperty("stream", true)
        return root.toString()
    }

    private fun describeHttpError(response: Response, body: String, messages: Messages): String {
        val detail = SseParser.readStringField(body, "message")
            ?: body.take(200).ifBlank { messages.noContent }
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
