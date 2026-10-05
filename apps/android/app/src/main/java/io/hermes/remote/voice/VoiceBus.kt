package io.hermes.remote.voice

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide signal used by [VoiceAssistantService] to tell the UI that the
 * "Hey Hermes" wake word fired (or that wake word is unavailable), without the
 * service and activity holding references to each other.
 *
 * Emissions are delivered to whichever UI is currently collecting. Extra
 * capacity is small and DROP_OLDEST so a burst of wake words never blocks the
 * detector thread.
 */
object VoiceBus {

    /** A wake-word (or setup problem) event the UI should react to. */
    enum class Event { WAKE, WAKE_UNAVAILABLE }

    private val _events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )

    /** Collect from the UI layer to receive wake events. */
    val events: SharedFlow<Event> = _events.asSharedFlow()

    /** Called by the service when "Hey Hermes" is detected. */
    fun emitWake() {
        _events.tryEmit(Event.WAKE)
    }

    /** Called by the service when the wake engine could not start. */
    fun emitWakeUnavailable() {
        _events.tryEmit(Event.WAKE_UNAVAILABLE)
    }
}
