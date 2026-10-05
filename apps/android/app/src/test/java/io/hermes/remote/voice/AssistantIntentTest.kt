package io.hermes.remote.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the Assistant → app hand-off parsing.
 *
 * This is the seam that decides whether "Hey Google, ask Hermes <x>" reaches
 * the PC at all, and it is the one part of the Assistant integration that can be
 * fully verified without Google Play Services. The intent shapes exercised here
 * mirror what Google's App Actions tooling actually delivers.
 */
class AssistantIntentTest {

    // --- clean(): stripping the app invocation ------------------------------

    @Test
    fun stripsPlainHermesPrefix() {
        assertEquals(
            "cho tôi biết trạng thái git",
            AssistantIntent.clean("Hermes, cho tôi biết trạng thái git"),
        )
    }

    @Test
    fun stripsHeyHermesPrefix() {
        assertEquals(
            "trạng thái git",
            AssistantIntent.clean("Hey Hermes trạng thái git"),
        )
    }

    @Test
    fun stripsAskHermesPrefix() {
        assertEquals(
            "tắt máy sau 60 giây",
            AssistantIntent.clean("ask Hermes tắt máy sau 60 giây"),
        )
    }

    @Test
    fun stripsVietnameseInvocationPrefixes() {
        assertEquals("kiểm tra RAM", AssistantIntent.clean("hỏi Hermes kiểm tra RAM"))
        assertEquals("kiểm tra RAM", AssistantIntent.clean("mở Hermes kiểm tra RAM"))
        assertEquals("kiểm tra RAM", AssistantIntent.clean("nhắn Hermes kiểm tra RAM"))
    }

    @Test
    fun stripsMultiWordProductName() {
        assertEquals("chạy test", AssistantIntent.clean("Hermes Remote, chạy test"))
    }

    @Test
    fun leavesTextWithoutInvocationUntouched() {
        assertEquals(
            "kiểm tra dung lượng đĩa",
            AssistantIntent.clean("kiểm tra dung lượng đĩa"),
        )
    }

    @Test
    fun aBareInvocationIsNotAPrompt() {
        // "Hey Google, ask Hermes" with nothing after it must not send a turn:
        // it would burn a round trip and speak a nonsense reply.
        assertNull(AssistantIntent.clean("Hermes"))
        assertNull(AssistantIntent.clean("hey hermes"))
        assertNull(AssistantIntent.clean("Hermes,"))
        assertNull(AssistantIntent.clean("hỏi Hermes"))
        assertNull(AssistantIntent.clean("   "))
        assertNull(AssistantIntent.clean(""))
        assertNull(AssistantIntent.clean(null))
    }

    @Test
    fun isCaseInsensitive() {
        assertEquals("chạy test", AssistantIntent.clean("HERMES chạy test"))
        assertEquals("chạy test", AssistantIntent.clean("hermes: chạy test"))
    }

    // --- parse(): the delivery shapes ---------------------------------------

    @Test
    fun readsOurOwnQueryExtra() {
        assertEquals(
            "trạng thái git",
            AssistantIntent.parse(mapOf("query" to "trạng thái git")),
        )
    }

    @Test
    fun readsAssistantTextExtra() {
        assertEquals(
            "trạng thái git",
            AssistantIntent.parse(mapOf("android.intent.extra.TEXT" to "trạng thái git")),
        )
    }

    @Test
    fun readsQueryFromNestedParameterBundle() {
        // Built-in intents nest their parameters; this is the shape that the
        // previous flat-extra-only parsing silently dropped.
        val bundles = mapOf(
            "com.google.assistant.extra.parameters" to mapOf("query" to "chạy test"),
        )
        assertEquals("chạy test", AssistantIntent.parse(emptyMap(), bundles))
    }

    @Test
    fun readsFeatureKeyFromNestedBundle() {
        val bundles = mapOf("parameters" to mapOf("feature" to "git status"))
        assertEquals("git status", AssistantIntent.parse(emptyMap(), bundles))
    }

    @Test
    fun readsDeepLinkQuery() {
        assertEquals(
            "chạy test",
            AssistantIntent.parse(emptyMap(), emptyMap(), "chạy test"),
        )
    }

    @Test
    fun prefersFlatExtraOverBundle() {
        val bundles = mapOf("parameters" to mapOf("query" to "from bundle"))
        assertEquals(
            "from extra",
            AssistantIntent.parse(mapOf("query" to "from extra"), bundles),
        )
    }

    @Test
    fun emptyBundleIsSkippedNotCrashed() {
        assertNull(AssistantIntent.parse(emptyMap(), mapOf("parameters" to emptyMap())))
    }

    @Test
    fun noUsableTextReturnsNull() {
        assertNull(AssistantIntent.parse(emptyMap()))
        assertNull(AssistantIntent.parse(mapOf("query" to null)))
        assertNull(AssistantIntent.parse(mapOf("query" to "   ")))
    }

    @Test
    fun stripsInvocationArrivingThroughAnExtra() {
        assertEquals(
            "trạng thái git",
            AssistantIntent.parse(mapOf("query" to "Hermes, trạng thái git")),
        )
    }

    // --- Contract with shortcuts.xml ----------------------------------------

    @Test
    fun queryExtraKeyMatchesTheCapabilityDeclaration() {
        // res/xml/shortcuts.xml declares android:key="query" for both
        // capabilities. If that drifts, the Assistant's payload stops resolving.
        assertEquals("query", AssistantIntent.QUERY_EXTRA_KEYS.first())
    }
}
