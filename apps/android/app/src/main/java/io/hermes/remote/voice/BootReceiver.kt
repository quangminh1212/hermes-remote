package io.hermes.remote.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.hermes.remote.data.ConfigStore

/**
 * Re-arms the always-listening voice assistant after the device reboots, but
 * only if the user had enabled it (the preference survives reboots).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (ConfigStore(context).isVoiceAssistantEnabled()) {
            VoiceAssistantService.start(context)
        }
    }
}
