package io.hermes.remote.ui

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
 * on the emulator, because it needs a real [io.hermes.remote.data.ConfigStore]
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
        private var speakReplies: Boolean = true,
    ) : VoiceController {
        var lastSpoken: String? = null
        var stopped = false
        var startListeningCalls = 0

        override fun isMicAvailable() = micAvailable
        override fun isSpeakRepliesEnabled() = speakReplies

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

        /** Continuous conversation: the ViewModel pushes completion here. */
        override fun onSpeakFinished() = Unit
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
        assertTrue(VoiceUiState().micAvailable)
    }

    @Test
    fun conversationModeDefaultsOff() {
        // Continuous conversation must default OFF: it opens the microphone
        // without the user asking, so it can never be on by surprise.
        assertFalse(VoiceUiState().conversationMode)
        assertTrue(VoiceUiState(conversationMode = true).conversationMode)
    }

    @Test
    fun noopControllerIsInert() {
        assertFalse(NoopVoiceController.isMicAvailable())
        assertFalse(NoopVoiceController.isSpeakRepliesEnabled())
        // Must not throw.
        NoopVoiceController.startListening({}, {}, {})
        NoopVoiceController.speak("x")
        NoopVoiceController.stopListening()
        NoopVoiceController.onSpeakFinished()
    }
}
