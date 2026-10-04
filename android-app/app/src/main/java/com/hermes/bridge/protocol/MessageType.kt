package com.hermes.bridge.protocol

/**
 * Wire protocol shared with the Python relay server.
 *
 * Server -> Device:
 *   { "type": "auth.grant", "session_token": str, "permissions": [str], "device_info": {...} }
 *   { "type": "auth.reject", "reason": str }
 *   { "type": "command.execute", "request_id": str, "action": str, "params": {...}, "timeout_ms": int }
 *
 * Device -> Server:
 *   { "type": "auth.request", "pairing_code": str }
 *   { "type": "command.complete", "request_id": str, "success": bool, "result": {...} }
 *   { "type": "command.error", "request_id": str, "error": str }
 */
object MessageType {
    const val AUTH_REQUEST = "auth.request"
    const val AUTH_GRANT = "auth.grant"
    const val AUTH_REJECT = "auth.reject"
    const val COMMAND_EXECUTE = "command.execute"
    const val COMMAND_COMPLETE = "command.complete"
    const val COMMAND_ERROR = "command.error"
}

/** Action names understood by the device. Mirrors `handle_*` in relay_server.py. */
object Action {
    const val TAP = "tap"
    const val TYPE_TEXT = "type_text"
    const val SWIPE = "swipe"
    const val GET_SCREENSHOT = "get_screenshot"
    const val READ_UI_TREE = "read_ui_tree"
    const val LAUNCH_APP = "launch_app"
}
