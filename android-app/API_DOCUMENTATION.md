# 📡 Hermes Android Bridge - API Documentation

## Overview

Android app communicates with Python relay server via WebSocket protocol. All commands are sent as JSON messages over the WebSocket connection.

## Protocol Structure

### Message Format

```json
{
  "type": "message_type",
  "request_id": "optional_request_identifier",
  "params": { ... }
}
```

## Connection Flow

### 1. Authentication Request (Android → Server)

```json
{
  "type": "auth.request",
  "pairing_code": "ABC-123"
}
```

#### Response: Success
```json
{
  "type": "auth.grant",
  "session_token": "xyz789",
  "permissions": ["tap", "type_text", "swipe", "ui_read"]
}
```

#### Response: Failed
```json
{
  "type": "auth.reject",
  "reason": "Invalid pairing code or device not found"
}
```

---

## Commands

All commands follow this pattern:

### Command Request (Server → Android)

```json
{
  "type": "command.execute",
  "request_id": "cmd_001",
  "action": "command_name",
  "params": { /* command parameters */ },
  "timeout_ms": 5000
}
```

### Command Response (Android → Server)

**Success:**
```json
{
  "type": "command.complete",
  "request_id": "cmd_001",
  "success": true,
  "result": {
    "data_field": "value"
  }
}
```

**Error:**
```json
{
  "type": "command.error",
  "request_id": "cmd_001",
  "error": "Error message here"
}
```

---

## Command Types

### 1. Tap Action

Execute a tap at specified coordinates.

**Request:**
```json
{
  "action": "tap",
  "params": {
    "x": 500,           // X coordinate in pixels
    "y": 800,           // Y coordinate in pixels
    "duration_ms": 100  // Optional: hold duration in ms
  }
}
```

**Response:**
```json
{
  "success": true,
  "coordinates": "500,800"
}
```

---

### 2. Type Text

Insert text into active input field.

**Request:**
```json
{
  "action": "type_text",
  "params": {
    "text": "Hello World!"  // Text to insert
  }
}
```

**Response:**
```json
{
  "success": true,
  "text_length": 12
}
```

**Note:** The input field must already have focus. For better compatibility, use scroll action first if needed.

---

### 3. Swipe Gesture

Perform a swipe gesture from start point to end point.

**Request:**
```json
{
  "action": "swipe",
  "params": {
    "start_x": 500,        // Start X coordinate
    "start_y": 800,        // Start Y coordinate
    "end_x": 500,          // End X coordinate
    "end_y": 400,          // End Y coordinate
    "duration_ms": 300     // Swipe duration in ms
  }
}
```

**Response:**
```json
{
  "success": true,
  "gesture": "swipe"
}
```

---

### 4. Get Screenshot

Capture current screen and return as base64-encoded PNG.

**Request:**
```json
{
  "action": "get_screenshot"
}
```

**Response:**
```json
{
  "success": true,
  "screenshot_base64": "iVBORw0KGgoAAAANSUhEUgAA..."  // Base64 string of PNG image
}
```

**Current Limitation:** The implementation currently returns an empty bitmap placeholder. Future versions will implement MediaProjection API for actual screen capture.

---

### 5. Read UI Tree

Extract accessibility tree structure from current screen.

**Request:**
```json
{
  "action": "read_ui_tree",
  "params": {
    "depth_limit": 10  // Maximum depth to traverse (default: 10)
  }
}
```

**Response:**
```json
{
  "success": true,
  "ui_tree_json": {
    "className": "android.widget.TextView",
    "packageName": "com.android.chrome",
    "text": "Example Text",
    "bounds": {
      "left": 0,
      "top": 0,
      "right": 1080,
      "bottom": 2400
    },
    "clickable": false,
    "focusable": false,
    "children": [
      {
        "className": "android.view.View",
        "text": "",
        "bounds": {...},
        "clickable": true,
        "children": []
      }
    ]
  }
}
```

**UI Node Properties:**
- `className`: Fully qualified class name
- `packageName`: Package name of the app
- `text`: Visible text content
- `description`: Content description (for accessibility)
- `resourceId`: Resource ID (e.g., "com.app:id/button")
- `bounds`: Rectangular bounds with left, top, right, bottom
- `clickable`, `longClickable`, `editable`, `focusable`, `selected`, `password`, `enabled`: Boolean states
- `viewIdResourceName`: Human-readable view ID name
- `children`: Array of child nodes (recursive structure)

---

### 6. Launch Application

Open specified application by package name.

**Request:**
```json
{
  "action": "launch_app",
  "params": {
    "package_name": "com.android.chrome"  // Full package name
  }
}
```

**Response:**
```json
{
  "success": true,
  "package": "com.android.chrome"
}
```

**Examples:**
- Chrome: `com.android.chrome`
- Gmail: `com.google.android.gm`
- Settings: `com.android.settings`

---

### 7. Press Hardware Key

Simulate hardware key press events.

**Request:**
```json
{
  "action": "press_key",
  "params": {
    "key_code": 3  // Android KeyEvent constant
  }
}
```

**Key Code Reference:**
| KeyCode | Constant | Value | Description |
|---------|----------|-------|-------------|
| HOME | KEYCODE_HOME | 3 | Home button |
| BACK | KEYCODE_BACK | 4 | Back button |
| RECENT | KEYCODE_RECENT | 187 | Recent apps |
| VOLUME_UP | KEYCODE_VOLUME_UP | 24 | Volume up |
| VOLUME_DOWN | KEYCODE_VOLUME_DOWN | 25 | Volume down |
| MUTE | KEYCODE_MUTE | 91 | Mute toggle |

**Convenience Actions:**

Instead of sending key codes directly, use these predefined actions:

```json
{"action": "press_home"}
{"action": "press_back"}
{"action": "press_recent_apps"}
{"action": "press_volume_up"}
{"action": "press_volume_down"}
{"action": "press_mute"}
```

**Response:**
```json
{
  "success": true,
  "key_pressed": "HOME"
}
```

---

### 8. Scroll

Scroll window in specified direction.

**Request:**
```json
{
  "action": "scroll",
  "params": {
    "direction": "down"  // Options: up, down, left, right, forward, backward
  }
}
```

**Response:**
```json
{
  "success": true,
  "direction": "down"
}
```

---

### 9. Clear Clipboard

Clear clipboard contents.

**Request:**
```json
{
  "action": "clear_clipboard"
}
```

**Response:**
```json
{
  "success": true,
  "cleared": true
}
```

---

### 10. Get Device Info

Retrieve device specifications and information.

**Request:**
```json
{
  "action": "get_device_info"
}
```

**Response:**
```json
{
  "success": true,
  "device_info": {
    "model": "Pixel 7 Pro",
    "manufacturer": "Google",
    "brand": "google",
    "sdkVersion": 34,
    "androidVersion": "14",
    "product": "lynx",
    "screenWidth": 1440,
    "screenHeight": 3120,
    "density": 3.5
  }
}
```

**Device Info Fields:**
- `model`: Device model name
- `manufacturer`: Device manufacturer
- `brand`: Brand name
- `sdkVersion`: Android SDK version number
- `androidVersion`: Android release version string
- `product`: Product codename
- `screenWidth`: Screen width in pixels
- `screenHeight`: Screen height in pixels
- `density`: Screen density in DP/dp ratio

---

## Error Handling

### Timeout Errors

If a command takes longer than specified timeout:

```json
{
  "type": "command.error",
  "request_id": "cmd_001",
  "error": "Command timeout exceeded"
}
```

### Validation Errors

When command parameters are invalid:

```json
{
  "type": "command.error",
  "request_id": "cmd_001",
  "error": "Missing required parameter: x"
}
```

### Execution Errors

When command execution fails:

```json
{
  "type": "command.error",
  "request_id": "cmd_001",
  "error": "Input field not focused. Try scrolling or tapping input first."
}
```

---

## Example Usage

### JavaScript/Python Client Example

```javascript
// Connect to WebSocket
const ws = new WebSocket('ws://localhost:8765/ws');

ws.onopen = () => {
  console.log('Connected to Hermes Bridge');
  
  // Authenticate
  ws.send(JSON.stringify({
    type: 'auth.request',
    pairing_code: 'ABC-123'
  }));
};

ws.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  
  if (msg.type === 'auth.grant') {
    console.log('Authenticated successfully');
    
    // Send a tap command
    ws.send(JSON.stringify({
      type: 'command.execute',
      request_id: 'cmd_001',
      action: 'tap',
      params: { x: 500, y: 800 }
    }));
  }
};

// Listen for command completion
function handleCommandComplete(msg) {
  console.log('Command completed:', msg.result);
}

function handleCommandError(msg) {
  console.error('Command failed:', msg.error);
}
```

### Python Example

```python
import asyncio
import websockets
import json

async def connect_to_android():
    async with websockets.connect('ws://localhost:8765/ws') as websocket:
        
        # Authenticate
        await websocket.send(json.dumps({
            'type': 'auth.request',
            'pairing_code': 'ABC-123'
        }))
        
        auth_response = await websocket.recv()
        print(f"Auth response: {auth_response}")
        
        # Send tap command
        cmd = {
            'type': 'command.execute',
            'request_id': 'cmd_001',
            'action': 'tap',
            'params': {'x': 500, 'y': 800}
        }
        
        await websocket.send(json.dumps(cmd))
        
        # Wait for response
        result = await websocket.recv()
        print(f"Command result: {result}")

# Run the connection
asyncio.run(connect_to_android())
```

---

## Rate Limiting & Performance Considerations

### Recommended Delays Between Commands

- **Tap operations:** Minimum 10ms between taps
- **Text input:** 50ms per character
- **Swipe gestures:** 100ms between move events
- **UI tree queries:** Minimum 500ms between calls

### Batch Operations

For multiple actions, consider batching where possible:

```json
// Instead of sequential taps
{ "action": "tap", "params": {"x": 500, "y": 800} }
{ "action": "tap", "params": {"x": 600, "y": 800} }
{ "action": "tap", "params": {"x": 700, "y": 800} }

// Use single multi-tap if supported (future enhancement)
{
  "action": "multi_tap",
  "params": {
    "taps": [
      {"x": 500, "y": 800},
      {"x": 600, "y": 800},
      {"x": 700, "y": 800}
    ],
    "delay_between_taps_ms": 200
  }
}
```

---

## Security Considerations

### Encryption

Currently using plain WebSocket (`ws://`). For production, upgrade to secure WebSocket (`wss://`) with TLS encryption.

### Authorization

The pairing code mechanism provides basic authentication. In future versions, add:
- Token-based session management
- IP whitelisting
- Encrypted communication channels

### Permission Model

Implemented permissions are defined by the server during authentication. Currently supports:
- Basic input control (tap, type, swipe)
- UI reading capabilities
- System controls

Future enhancements may include:
- Camera access
- Microphone access
- File system access

---

## Testing Checklist

- [ ] Authentication works with valid pairing code
- [ ] Rejection occurs with invalid pairing code
- [ ] Tap at screen center works
- [ ] Tap near edges handles coordinate limits
- [ ] Long text (>50 chars) inputs correctly
- [ ] Unicode characters supported (emojis, special chars)
- [ ] Swipe gestures work smoothly
- [ ] Multi-touch swipe handled properly
- [ ] UI tree extraction includes all visible elements
- [ ] Screenshot returns valid base64 data
- [ ] App launch switches contexts correctly
- [ ] Hardware keys work across different apps
- [ ] Scroll works on both vertically and horizontally scrolling views
- [ ] Clipboard clearing succeeds
- [ ] Device info matches physical device specs
- [ ] Commands timeout after specified duration
- [ ] Disconnected state handled gracefully
- [ ] Auto-reconnection restores session

---

## Version History

### v0.1.0-alpha (Current)
- Basic tap/swipe/touch support
- Text input via keyboard events
- UI tree extraction
- Device information retrieval
- Screenshot (placeholder implementation)
- Hardware key simulation
- Scroll functionality
- Clipboard management

### Upcoming Features
- MediaProjection screenshot capture
- Keyboard overlay for easier text input
- Multi-touch gesture support
- Gesture recording/replay
- Command queuing and rate limiting
- Diagnostic dashboard
- Session persistence
- Backup/restore configuration
