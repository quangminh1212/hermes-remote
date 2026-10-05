package io.hermes.remote.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectPayloadTest {

    private val validJson =
        """{"v":1,"baseUrl":"https://abc.trycloudflare.com","fallbackUrl":"http://192.168.1.113:8642","apiKey":"k1234567890123456","profile":"","model":"Claude-Fable.3","profiles":["dalek","doraemon"]}"""

    @Test
    fun `parses a full payload from the npm pack`() {
        val p = ConnectPayload.parse(validJson)!!
        assertEquals(1, p.version)
        assertEquals("https://abc.trycloudflare.com", p.baseUrl)
        assertEquals("http://192.168.1.113:8642", p.fallbackUrl)
        assertEquals("k1234567890123456", p.apiKey)
        assertEquals("", p.profile)
        assertEquals("Claude-Fable.3", p.model)
        assertEquals(listOf("dalek", "doraemon"), p.profiles)
        assertTrue(p.isUsable())
    }

    @Test
    fun `maps to a ServerConfig for the connection`() {
        val cfg = ConnectPayload.parse(validJson)!!.toServerConfig()
        assertEquals("https://abc.trycloudflare.com", cfg.baseUrl)
        assertEquals("k1234567890123456", cfg.apiKey)
        assertEquals("", cfg.profile)
        assertEquals("Claude-Fable.3", cfg.model)
    }

    @Test
    fun `parses the agent list and default agent from the bridge payload`() {
        val json =
            """{"v":1,"baseUrl":"http://10.0.0.5:8643","fallbackUrl":"http://10.0.0.5:8643","apiKey":"k","profile":"","model":"claude","profiles":[],"agents":["claude","codex","Anthropic"],"defaultAgent":"codex"}"""
        val p = ConnectPayload.parse(json)!!
        assertEquals(listOf("claude", "codex", "Anthropic"), p.agents)
        assertEquals("codex", p.defaultAgent)
        // The bridge URL is what the phone should connect to.
        assertEquals("http://10.0.0.5:8643", p.baseUrl)
        assertTrue(p.isUsable())
    }

    @Test
    fun `tolerates a payload without the agent fields`() {
        // Older npm pack versions omitted agents/defaultAgent; the app must
        // still configure itself and simply offer no agent picker.
        val p = ConnectPayload.parse(validJson)!!
        assertEquals(emptyList<String>(), p.agents)
        assertEquals("", p.defaultAgent)
    }

    @Test
    fun `rejects an unrelated QR code`() {
        assertNull(ConnectPayload.parse("https://example.com/some/link"))
        assertNull(ConnectPayload.parse("just some text"))
        assertNull(ConnectPayload.parse(""))
        assertNull(ConnectPayload.parse(null))
    }

    @Test
    fun `rejects payload missing server url or key`() {
        assertNull(ConnectPayload.parse("""{"baseUrl":"","apiKey":"x"}"""))
        assertNull(ConnectPayload.parse("""{"baseUrl":"https://x","apiKey":""}"""))
    }

    @Test
    fun `isUsable reflects required fields`() {
        assertFalse(ConnectPayload().isUsable())
        assertTrue(ConnectPayload(baseUrl = "https://x", apiKey = "y").isUsable())
    }
}
