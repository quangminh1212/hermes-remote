package com.hermes.bridge.data

import com.hermes.bridge.model.ChatMessage
import com.hermes.bridge.model.Role
import com.hermes.bridge.model.ServerConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end exercise of [ChatApi] against a fake SSE server: proves the
 * request shape (auth header, path, stream flag) and the streamed decoding.
 */
class ChatApiStreamTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun config() = ServerConfig(
        baseUrl = server.url("/").toString().trimEnd('/'),
        apiKey = "test-key",
        profile = "dalek",
        model = "glm-5.3",
    )

    @Test
    fun streamsDeltasAndAssemblesFullReply() {
        val sse = buildString {
            append("data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"Xin \"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"chao\"}}]}\n\n")
            append("data: [DONE]\n\n")
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody(sse))

        val deltas = mutableListOf<String>()
        var completed: String? = null
        val full = ChatApi().streamChat(config(), listOf(ChatMessage("1", Role.USER, "hi"))) { ev ->
            when (ev) {
                is StreamEvent.Delta -> deltas += ev.text
                is StreamEvent.Completed -> completed = ev.fullText
                is StreamEvent.Failed -> error("unexpected failure: ${ev.message}")
            }
        }

        assertEquals("Xin chao", full)
        assertEquals(listOf("Xin ", "chao"), deltas)
        assertEquals("Xin chao", completed)

        val request = server.takeRequest()
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
        assertEquals(
            "/p/dalek/v1/chat/completions",
            request.path,
        )
        val body = request.body.readUtf8()
        assertTrue("request must ask for a stream", body.contains("\"stream\":true"))
        assertTrue(body.contains("\"model\":\"glm-5.3\""))
        assertTrue(body.contains("\"role\":\"user\""))
    }

    @Test
    fun surfacesHttpErrorAsUserReadableMessage() {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("{\"error\":{\"message\":\"invalid api key\"}}"),
        )
        var failure: String? = null
        ChatApi().streamChat(config(), listOf(ChatMessage("1", Role.USER, "hi"))) {
            if (it is StreamEvent.Failed) failure = it.message
        }
        assertTrue(failure != null && failure!!.contains("401"))
        assertTrue(failure!!.contains("invalid api key"))
    }

    @Test
    fun listsModelsFromServer() {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("{\"data\":[{\"id\":\"glm-5.3\"},{\"id\":\"glm-air\"}]}"),
        )
        val models = ChatApi().listModels(config())
        assertEquals(listOf("glm-5.3", "glm-air"), models)
    }
}
