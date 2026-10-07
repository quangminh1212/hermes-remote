package io.hermes.remote.model

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

    // --- Agent selection ---------------------------------------------------
    //
    // The bridge routes on the request's `model` field, so a model that is not
    // one of the reported agents is rejected with 404. These pin the fallback
    // that stops a freshly-paired phone failing on its first message.

    private fun withAgents(model: String, defaultAgent: String = "") = ServerConfig(
        baseUrl = "http://10.0.0.5:8643",
        apiKey = "k",
        profile = "",
        model = model,
        agents = listOf("claude", "codex", "grok", "opencode"),
        defaultAgent = defaultAgent,
    )

    @Test
    fun `a gateway model name is retargeted when agents are reported`() {
        // This is the exact bug: DEFAULT_MODEL is a gateway model, not an agent.
        val config = withAgents(ServerConfig.DEFAULT_MODEL, defaultAgent = "claude")
        assertEquals("claude", config.effectiveModel)
        assertTrue(config.modelWasRetargeted)
    }

    @Test
    fun `a valid agent id is used as-is`() {
        val config = withAgents("codex")
        assertEquals("codex", config.effectiveModel)
        assertFalse(config.modelWasRetargeted)
    }

    @Test
    fun `agent matching ignores case and surrounding space`() {
        assertEquals("codex", withAgents("  CoDeX  ").effectiveModel)
    }

    @Test
    fun `without a default agent the first reported agent is chosen`() {
        assertEquals("claude", withAgents(ServerConfig.DEFAULT_MODEL).effectiveModel)
    }

    @Test
    fun `an unusable default agent is skipped`() {
        // defaultAgent naming something absent must not be sent.
        val config = withAgents(ServerConfig.DEFAULT_MODEL, defaultAgent = "not-installed")
        assertEquals("claude", config.effectiveModel)
    }

    @Test
    fun `a default agent naming a real agent is preferred`() {
        val config = withAgents(ServerConfig.DEFAULT_MODEL, defaultAgent = "grok")
        assertEquals("grok", config.effectiveModel)
    }

    @Test
    fun `with no agents reported the model passes through untouched`() {
        // Bridge off: the model really is a gateway model name, so keep it.
        val config = ServerConfig("http://x", "k", "", "Claude-Fable.3")
        assertFalse(config.hasAgents)
        assertEquals("Claude-Fable.3", config.effectiveModel)
        assertFalse(config.modelWasRetargeted)
    }

    @Test
    fun `agentChoices puts the effective model first and removes duplicates`() {
        val config = withAgents("codex")
        assertEquals(listOf("codex", "claude", "grok", "opencode"), config.agentChoices)
    }

    @Test
    fun `agentChoices ignores blanks and duplicates from the wire`() {
        val config = ServerConfig(
            baseUrl = "http://x", apiKey = "k", profile = "", model = "claude",
            agents = listOf("claude", "", "  ", "CLAUDE", "codex"),
        )
        assertEquals(listOf("claude", "codex"), config.agentChoices)
    }

    @Test
    fun `agentChoices is empty when no agents are reported`() {
        val config = ServerConfig("http://x", "k", "", "Claude-Fable.3")
        assertTrue(config.agentChoices.isEmpty())
    }

    @Test
    fun `a single reported agent is not treated as a choice`() {
        // The bridge now ships exactly one agent, so this is the common case.
        // The UI hides the picker at or below one option, which is why the
        // boundary is asserted here rather than only in the composable.
        val config = ServerConfig(
            baseUrl = "http://x", apiKey = "k", profile = "", model = "hermes",
            agents = listOf("hermes"),
        )
        assertEquals(listOf("hermes"), config.agentChoices)
        assertTrue(
            "one agent must not be presented as a picker",
            config.agentChoices.size <= 1,
        )
    }

    @Test
    fun `a stale default model still resolves to the one surviving agent`() {
        // A phone paired before the CLI removal has model = a gateway name or a
        // removed CLI id. The first message must not 404.
        for (stale in listOf("Claude-Fable.3", "claude", "grok", "glm-5.3")) {
            val config = ServerConfig(
                baseUrl = "http://x", apiKey = "k", profile = "", model = stale,
                agents = listOf("hermes"), defaultAgent = "",
            )
            assertEquals(
                "model $stale should retarget to hermes",
                "hermes",
                config.effectiveModel,
            )
            assertTrue(config.modelWasRetargeted)
        }
    }
}
