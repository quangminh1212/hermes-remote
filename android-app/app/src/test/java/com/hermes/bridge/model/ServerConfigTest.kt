package com.hermes.bridge.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConfigTest {

    @Test
    fun buildsChatEndpointFromBareHostWithProfile() {
        val config = ServerConfig(
            baseUrl = "https://abc.trycloudflare.com",
            apiKey = "k",
            profile = "dalek",
            model = "glm-5.3",
        )
        assertEquals(
            "https://abc.trycloudflare.com/p/dalek/v1/chat/completions",
            config.chatEndpoint,
        )
        assertEquals(
            "https://abc.trycloudflare.com/p/dalek/v1/models",
            config.modelsEndpoint,
        )
    }

    @Test
    fun acceptsHostAlreadyContainingProfile() {
        val config = ServerConfig(
            baseUrl = "http://10.0.2.2:8642/p/doraemon",
            apiKey = "k",
            profile = "doraemon",
            model = "m",
        )
        assertEquals(
            "http://10.0.2.2:8642/p/doraemon/v1/chat/completions",
            config.chatEndpoint,
        )
    }

    @Test
    fun acceptsUrlAlreadyEndingInV1() {
        val config = ServerConfig(
            baseUrl = "https://host/p/dalek/v1/",
            apiKey = "k",
            profile = "dalek",
            model = "m",
        )
        assertEquals(
            "https://host/p/dalek/v1/chat/completions",
            config.chatEndpoint,
        )
    }

    @Test
    fun acceptsFullChatCompletionsUrl() {
        val config = ServerConfig(
            baseUrl = "https://host/p/dalek/v1/chat/completions",
            apiKey = "k",
            profile = "dalek",
            model = "m",
        )
        assertEquals(
            "https://host/p/dalek/v1/chat/completions",
            config.chatEndpoint,
        )
    }

    @Test
    fun withoutProfileOmitsProfileSegment() {
        val config = ServerConfig(
            baseUrl = "https://host",
            apiKey = "k",
            profile = "",
            model = "m",
        )
        assertEquals("https://host/v1/chat/completions", config.chatEndpoint)
    }

    @Test
    fun defaultProfileIsBlankAndTargetsTopLevelV1() {
        // Regression guard: a default profile of "dalek" made the app hit
        // /p/dalek/v1 and fail closed with 401. Default must be blank so the
        // gateway uses the top-level API_SERVER_KEY.
        assertEquals("", ServerConfig.DEFAULT_PROFILE)
        val fresh = ServerConfig(
            baseUrl = "http://192.168.1.113:8642",
            apiKey = "k",
            profile = ServerConfig.DEFAULT_PROFILE,
            model = ServerConfig.DEFAULT_MODEL,
        )
        assertEquals(
            "http://192.168.1.113:8642/v1/chat/completions",
            fresh.chatEndpoint,
        )
        assertEquals(
            "http://192.168.1.113:8642/v1/models",
            fresh.modelsEndpoint,
        )
    }

    @Test
    fun validationRequiresHttpAndKey() {
        assertFalse(ServerConfig("ftp://x", "k", "p", "m").isValid())
        assertFalse(ServerConfig("https://x", "", "p", "m").isValid())
        assertTrue(ServerConfig("https://x", "k", "p", "m").isValid())
    }
}
