package io.hermes.remote.voice

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager

/**
 * Always-listening wake-word detector ("Hey Hermes") built on Porcupine.
 *
 * Porcupine needs a (free) Picovoice AccessKey and a keyword file. Both are
 * optional at build time: if either is missing or the engine fails to start,
 * [start] returns false and the app keeps working in tap-to-talk mode. This
 * class never throws into the caller for setup problems.
 */
class WakeWordDetector(private val context: Context) {

    private var manager: PorcupineManager? = null

    /** True while the detector is actively listening for the wake word. */
    var isRunning = false
        private set

    /**
     * Begin listening. [onDetected] fires (on a background thread) each time the
     * wake word is heard. Returns true on success, false if the engine could not
     * start (caller should fall back to tap-to-talk).
     */
    fun start(accessKey: String, onDetected: () -> Unit): Boolean {
        if (isRunning) return true
        if (accessKey.isBlank()) {
            Log.i(TAG, "No Porcupine AccessKey configured — wake word disabled.")
            return false
        }
        return try {
            val keywordPaths = extractKeywords()
            if (keywordPaths.isEmpty()) {
                Log.i(TAG, "No wake-word .ppn file in assets — wake word disabled.")
                return false
            }
            manager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPaths(keywordPaths.toTypedArray())
                .setSensitivities(FloatArray(keywordPaths.size) { DEFAULT_SENSITIVITY })
                .build(context) { _ -> onDetected() }
            manager?.start()
            isRunning = true
            true
        } catch (e: PorcupineException) {
            Log.w(TAG, "Porcupine init failed: ${e.message}")
            isRunning = false
            false
        } catch (e: Exception) {
            Log.w(TAG, "Wake word unavailable: ${e.message}")
            isRunning = false
            false
        }
    }

    /** Stop listening and release the engine. Safe to call when not running. */
    fun stop() {
        try {
            manager?.stop()
            manager?.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Wake word stop: ${e.message}")
        } finally {
            manager = null
            isRunning = false
        }
    }

    /**
     * Copy every `.ppn` keyword bundled in `assets/` to the app's files dir and
     * return the resulting filesystem paths (Porcupine wants real files, not
     * asset handles). Copies are refreshed on each start so a rebuilt APK with a
     * new keyword takes effect. Supporting several files lets alternate
     * pronunciations ("Hey Hermes", "Ok Hermes") ship without code changes.
     */
    private fun extractKeywords(): List<String> {
        val names = context.assets.list("")?.filter { it.endsWith(".ppn") }.orEmpty()
        if (names.isEmpty()) return emptyList()

        val outDir = java.io.File(context.filesDir, "keywords").apply { mkdirs() }
        val paths = mutableListOf<String>()
        for (name in names) {
            val dest = java.io.File(outDir, name)
            // Re-extract when missing or when the asset is newer than the copy.
            val needsCopy = !dest.exists() || assetNewerThan(name, dest)
            if (needsCopy) {
                try {
                    context.assets.open(name).use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to extract $name: ${e.message}")
                    continue
                }
            }
            paths += dest.absolutePath
        }
        return paths
    }

    /** Best-effort check: does the bundled asset look newer than the copy on disk? */
    private fun assetNewerThan(name: String, dest: java.io.File): Boolean {
        return try {
            val entry = context.assets.openFd(name)
            val assetLen = entry.length
            entry.close()
            dest.length() != assetLen
        } catch (_: Exception) {
            // openFd fails when the asset is compressed; just keep the existing copy.
            false
        }
    }

    companion object {
        private const val TAG = "HermesWakeWord"

        /** Porcupine sensitivity: higher = more sensitive, more false positives. */
        const val DEFAULT_SENSITIVITY = 0.6f
    }
}
