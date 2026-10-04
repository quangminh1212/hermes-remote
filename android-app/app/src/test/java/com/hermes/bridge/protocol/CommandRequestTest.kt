package com.hermes.bridge.protocol

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRequestTest {

    private fun params(vararg pairs: Pair<String, Any>): JsonObject {
        val obj = JsonObject()
        pairs.forEach { (k, v) ->
            when (v) {
                is Int -> obj.addProperty(k, v)
                is Double -> obj.addProperty(k, v)
                is String -> obj.addProperty(k, v)
                else -> error("unsupported")
            }
        }
        return obj
    }

    @Test
    fun `double returns value when present`() {
        val req = CommandRequest("r1", Action.TAP, params("x" to 10.5), 1000)
        assertEquals(10.5, req.double("x"), 0.001)
    }

    @Test
    fun `double returns default when missing`() {
        val req = CommandRequest("r1", Action.TAP, JsonObject(), 1000)
        assertEquals(0.0, req.double("x"), 0.001)
    }

    @Test
    fun `int returns value when present`() {
        val req = CommandRequest("r1", Action.TAP, params("duration_ms" to 250), 1000)
        assertEquals(250, req.int("duration_ms", 100))
    }

    @Test
    fun `int returns default when missing`() {
        val req = CommandRequest("r1", Action.TAP, JsonObject(), 1000)
        assertEquals(100, req.int("duration_ms", 100))
    }

    @Test
    fun `string returns value and default`() {
        val req = CommandRequest("r1", Action.TYPE_TEXT, params("text" to "hello"), 1000)
        assertEquals("hello", req.string("text"))
        assertTrue(req.string("absent").isEmpty())
    }
}
