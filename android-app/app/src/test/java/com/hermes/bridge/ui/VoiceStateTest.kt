package com.hermes.bridge.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests the [VoiceController] contract with a scripted fake, and the shape of
 * [VoiceUiState] transitions the UI relies on.
 *
 * The full [MainViewModel] flow (listen → send → speak) is exercised end-to-end
 * on the emulator, because it needs a real [com.hermes.bridge.data.ConfigStore]
 * (SharedPreferences) which is not available on the plain JVM. What can be
 * pinned down here — and what regressions would most likely break — is the
 * controller contract and the phase enum used to drive the mic button.
 */
class VoiceStateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A scripted [VoiceController] for assertions. */
    private class FakeVoice(
        private var micAvailable: Boolean = true,
        private var assistantEnabled: Boolean = false,
        private var speakReplies: Boolean = true,
        private var wakeActive: Boolean = false,
    ) : VoiceController {
        var lastSpoken: String? = null
        var stopped = false
        var startListeningCalls = 0

        override fun isMicAvailable() = micAvailable
        override fun isAssistantEnabled() = assistantEnabled
        override fun isSpeakRepliesEnabled() = speakReplies
        override fun isWakeWordActive() = wakeActive

        override fun startListening(
            onPartial: (String) -> Unit,
            onResult: (String) -> Unit,
            onError: (String) -> Unit,
        ) {
            startListeningCalls++
        }

        override fun stopListening() {
            stopped = true
        }

        override fun speak(text: String) {
            lastSpoken = text
        }

        override fun stopSpeaking() = Unit

        override fun setAssistantEnabled(enabled: Boolean): Boolean {
            assistantEnabled = enabled
            return enabled
        }
    }

    @Test
    fun enableAssistantReflectsInController() {
        val v = FakeVoice()
        assertFalse(v.isAssistantEnabled())
        assertTrue(v.setAssistantEnabled(true))
        assertTrue(v.isAssistantEnabled())
        assertFalse(v.setAssistantEnabled(false))
        assertFalse(v.isAssistantEnabled())
    }

    @Test
    fun speakRecordsText() {
        val v = FakeVoice()
        v.speak("xin chào")
        assertEquals("xin chào", v.lastSpoken)
    }

    @Test
    fun stopListeningSetsFlag() {
        val v = FakeVoice()
        v.stopListening()
        assertTrue(v.stopped)
    }

    @Test
    fun micUnavailableIsReported() {
        assertFalse(FakeVoice(micAvailable = false).isMicAvailable())
        assertTrue(FakeVoice(micAvailable = true).isMicAvailable())
    }

    @Test
    fun voicePhaseStartsIdle() {
        assertEquals(VoicePhase.IDLE, VoiceUiState().phase)
        assertFalse(VoiceUiState().enabled)
        assertTrue(VoiceUiState().micAvailable)
    }

    @Test
    fun noopControllerIsInert() {
        assertFalse(NoopVoiceController.isMicAvailable())
        assertFalse(NoopVoiceController.isAssistantEnabled())
        assertFalse(NoopVoiceController.setAssistantEnabled(true))
        // Must not throw.
        NoopVoiceController.startListening({}, {}, {})
        NoopVoiceController.speak("x")
        NoopVoiceController.stopListening()
    }
}
