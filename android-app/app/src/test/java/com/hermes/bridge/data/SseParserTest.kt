package com.hermes.bridge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {

    @Test
    fun parsesStreamingDeltasAcrossChunkBoundaries() {
        val parser = SseParser()
        val body = buildString {
            append("data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"lo \"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"world\"}}]}\n\n")
            append("data: [DONE]\n\n")
        }
        val deltas = mutableListOf<String>()
        // Feed in awkward 7-char slices to exercise partial-line buffering.
        body.chunked(7).forEach { deltas += parser.feed(it) }

        assertEquals(listOf("Hel", "lo ", "world"), deltas)
        assertTrue(parser.isDone)
    }

    @Test
    fun ignoresRoleOnlyDeltaWithoutContent() {
        val deltas = SseParser().feed("data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n")
        assertTrue(deltas.isEmpty())
    }

    @Test
    fun ignoresCommentsAndBlankLines() {
        val parser = SseParser()
        val deltas = parser.feed(": keep-alive\n\nevent: ping\ndata: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}\n")
        assertEquals(listOf("x"), deltas)
    }

    @Test
    fun decodesEscapesInContent() {
        val deltas = SseParser().feed(
            "data: {\"choices\":[{\"delta\":{\"content\":\"line1\\nline2\\t\\\"q\\\"\"}}]}\n",
        )
        assertEquals(listOf("line1\nline2\t\"q\""), deltas)
    }

    @Test
    fun decodesUnicodeEscape() {
        val deltas = SseParser().feed(
            "data: {\"choices\":[{\"delta\":{\"content\":\"\\u0041\\u1EA1\"}}]}\n",
        )
        assertEquals(listOf("A\u1EA1"), deltas)
    }

    @Test
    fun fallsBackToNonStreamingMessageContent() {
        val deltas = SseParser().feed(
            "data: {\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"full reply\"}}]}\n",
        )
        assertEquals(listOf("full reply"), deltas)
    }

    @Test
    fun marksDoneOnSentinel() {
        val parser = SseParser()
        assertFalse(parser.isDone)
        parser.feed("data: [DONE]\n")
        assertTrue(parser.isDone)
    }
}
