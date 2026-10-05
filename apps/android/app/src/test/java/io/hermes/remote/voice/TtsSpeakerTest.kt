package io.hermes.remote.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the pure markdown→speech sanitizer (no Android framework). */
class TtsSpeakerTest {

    @Test
    fun stripsFencedCodeBlocks() {
        val input = "Đây là code:\n```kotlin\nfun main() {}\n```\nXong."
        val out = TtsSpeaker.sanitizeForSpeech(input)
        assertFalse(out.contains("fun main"))
        assertFalse(out.contains("```"))
        assertTrue(out.contains("Đây là code"))
        assertTrue(out.contains("Xong"))
    }

    @Test
    fun unwrapsMarkdownLinks() {
        val out = TtsSpeaker.sanitizeForSpeech("Xem [tài liệu](https://example.com) nhé.")
        assertTrue(out.contains("tài liệu"))
        assertFalse(out.contains("https://example.com"))
    }

    @Test
    fun replacesBareUrls() {
        // The replacement word is localised and injected, so assert on the word
        // that was passed in rather than on one hard-coded language.
        val out = TtsSpeaker.sanitizeForSpeech("Vào https://example.com/path để xem.", "liên kết")
        assertFalse(out.contains("http"))
        assertTrue(out.contains("liên kết"))
    }

    @Test
    fun usesTheInjectedLinkWord() {
        assertTrue(TtsSpeaker.sanitizeForSpeech("see https://x.dev", "链接").contains("链接"))
        assertTrue(TtsSpeaker.sanitizeForSpeech("see https://x.dev", "link").contains("link"))
    }

    @Test
    fun removesHeadingAndListMarkers() {
        val out = TtsSpeaker.sanitizeForSpeech("# Tiêu đề\n- mục một\n- mục hai\n1. mục ba")
        assertFalse(out.startsWith("#"))
        assertFalse(out.contains("- mục"))
        assertFalse(out.contains("1. mục"))
    }

    @Test
    fun removesEmphasisAndInlineCode() {
        val out = TtsSpeaker.sanitizeForSpeech("Đây là **đậm** và *nghiêng* và `code`.")
        assertFalse(out.contains("*"))
        assertFalse(out.contains("`"))
        assertTrue(out.contains("đậm"))
        assertTrue(out.contains("code"))
    }

    @Test
    fun collapsesWhitespace() {
        val out = TtsSpeaker.sanitizeForSpeech("a     b\n\n\n\nc")
        assertEquals("a b\nc", out)
    }

    @Test
    fun truncatesLongTextAtSentenceBoundary() {
        val long = (1..400).joinToString(" ") { "câu số $it." }
        val out = TtsSpeaker.sanitizeForSpeech(long)
        assertTrue(out.length <= TtsSpeaker.MAX_SPOKEN_CHARS + 2)
        assertTrue(out.endsWith("…"))
    }

    @Test
    fun blankInputYieldsEmpty() {
        assertEquals("", TtsSpeaker.sanitizeForSpeech("   "))
        assertEquals("", TtsSpeaker.sanitizeForSpeech(""))
    }
}
