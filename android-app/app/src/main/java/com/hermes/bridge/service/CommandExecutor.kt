package com.hermes.bridge.service

import android.util.Log
import com.hermes.bridge.protocol.Action
import com.hermes.bridge.protocol.CommandRequest
import com.hermes.bridge.protocol.CommandResult

/**
 * Executes protocol commands against the [AccessibilityBridgeService].
 * Runs on a background dispatcher (gesture APIs block waiting for completion).
 */
class CommandExecutor {

    private companion object {
        const val TAG = "CommandExecutor"
    }

    fun execute(command: CommandRequest): CommandResult {
        val service = AccessibilityBridgeService.instance
            ?: return CommandResult(false, emptyMap(), "Accessibility service not enabled")

        return try {
            when (command.action) {
                Action.TAP -> tap(service, command)
                Action.SWIPE -> swipe(service, command)
                Action.TYPE_TEXT -> typeText(service, command)
                Action.GET_SCREENSHOT -> screenshot(service)
                Action.READ_UI_TREE -> uiTree(service, command)
                Action.LAUNCH_APP -> launchApp(service, command)
                else -> CommandResult(false, emptyMap(), "Unknown action: ${command.action}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Command ${command.action} failed", e)
            CommandResult(false, emptyMap(), e.message ?: "unknown error")
        }
    }

    private fun tap(service: AccessibilityBridgeService, c: CommandRequest): CommandResult {
        val x = c.double("x").toFloat()
        val y = c.double("y").toFloat()
        val duration = c.int("duration_ms", 100).toLong()
        val ok = service.tap(x, y, duration)
        return CommandResult(ok, mapOf("x" to x, "y" to y), if (ok) null else "tap gesture failed")
    }

    private fun swipe(service: AccessibilityBridgeService, c: CommandRequest): CommandResult {
        val ok = service.swipe(
            c.double("start_x").toFloat(), c.double("start_y").toFloat(),
            c.double("end_x").toFloat(), c.double("end_y").toFloat(),
            c.int("duration_ms", 300).toLong(),
        )
        return CommandResult(ok, emptyMap(), if (ok) null else "swipe gesture failed")
    }

    private fun typeText(service: AccessibilityBridgeService, c: CommandRequest): CommandResult {
        val text = c.string("text")
        val ok = service.typeText(text)
        return CommandResult(ok, mapOf("length" to text.length), if (ok) null else "no editable field focused")
    }

    private fun screenshot(service: AccessibilityBridgeService): CommandResult {
        val b64 = service.screenshotBase64()
            ?: return CommandResult(false, emptyMap(), "screenshot unavailable (needs API 30+)")
        return CommandResult(true, mapOf("image_base64" to b64, "format" to "png"))
    }

    private fun uiTree(service: AccessibilityBridgeService, c: CommandRequest): CommandResult {
        val depth = c.int("depth_limit", 10)
        val tree = service.readUiTree(depth)
            ?: return CommandResult(false, emptyMap(), "no active window")
        return CommandResult(true, mapOf("tree" to tree))
    }

    private fun launchApp(service: AccessibilityBridgeService, c: CommandRequest): CommandResult {
        // Relay server sends the tool param `package_name`.
        val pkg = c.string("package_name").ifEmpty { c.string("package") }
        val ok = service.launchApp(pkg)
        return CommandResult(ok, mapOf("package" to pkg), if (ok) null else "cannot launch $pkg")
    }
}
