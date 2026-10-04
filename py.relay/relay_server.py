"""
Hermes Android Bridge - Python Relay Server
===========================================

Python plugin that acts as a bridge between Hermes Agent and Android devices.
This server listens for tool calls from the agent and relays them to connected mobile devices.

Usage: python relay_server.py --host 0.0.0.0 --port 8765
"""

import asyncio
import json
import logging
import uuid
from datetime import datetime
from typing import Optional, Dict, Any
from dataclasses import dataclass, field
from aiohttp import web, WSMsgType
import secrets

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


@dataclass
class DeviceSession:
    """Represents an authenticated device session"""
    device_id: str
    pairing_code: str
    session_token: str
    connected_at: datetime
    permissions: list = field(default_factory=list)
    
    @classmethod
    def generate_pairing_code(cls) -> str:
        return secrets.token_hex(3).upper()[:6]
    
    @classmethod
    def generate_session_token(cls) -> str:
        return secrets.token_urlsafe(32)


class WebSocketManager:
    """Manages WebSocket connections and message routing"""
    
    def __init__(self):
        self.sessions: Dict[str, DeviceSession] = {}
        self.websocket_connections: Dict[str, web.WebSocketResponse] = {}
        self.command_queue: asyncio.Queue = asyncio.Queue()
    
    async def authenticate_device(self, ws: web.WebSocketResponse, 
                                  pairing_code: str) -> Optional[DeviceSession]:
        """Authenticate a device with pairing code"""
        
        # Check if valid pairing code exists
        for device_id, session in self.sessions.items():
            if session.pairing_code == pairing_code:
                # Generate new session token
                session.session_token = DeviceSession.generate_session_token()
                
                # Send authentication success to client
                await ws.send_json({
                    "type": "auth.grant",
                    "session_token": session.session_token,
                    "permissions": ["tap", "type", "swipe", "screenshot", "ui_tree"],
                    "device_info": {
                        "id": session.device_id,
                        "paired_at": session.connected_at.isoformat()
                    }
                })
                
                logger.info(f"Device {device_id} authenticated successfully")
                return session
        
        # Invalid pairing code
        await ws.send_json({
            "type": "auth.reject",
            "reason": "Invalid or expired pairing code"
        })
        
        return None
    
    async def broadcast_command(self, command: dict, target_device_id: Optional[str] = None):
        """Send command to one or all devices"""
        
        devices_to_notify = [target_device_id] if target_device_id else list(self.sessions.keys())
        
        for device_id in devices_to_notify:
            if device_id in self.websocket_connections:
                try:
                    ws = self.websocket_connections[device_id]
                    await ws.send_json({
                        "type": "command.execute",
                        **command
                    })
                except Exception as e:
                    logger.error(f"Failed to send command to {device_id}: {e}")
    
    async def register_command_response(self, device_id: str, request_id: str, 
                                       result: dict, success: bool = True):
        """Register response from device action"""
        
        response = {
            "type": "response.received",
            "request_id": request_id,
            "success": success,
            "result": result,
            "timestamp": datetime.now().isoformat()
        }
        
        logger.info(f"Command {request_id} completed: {success}")
        return response


# Global WebSocket manager instance
ws_manager = WebSocketManager()


def create_hermes_tools() -> list:
    """Create Hermes Agent tools configuration"""
    
    return [
        {
            "name": "android_pair_device",
            "description": "Pair a new Android device with Hermes Agent via pairing code",
            "parameters": {
                "type": "object",
                "properties": {
                    "display_name": {
                        "type": "string",
                        "description": "Human-readable name for this device (e.g., 'My Phone')"
                    }
                },
                "required": ["display_name"]
            },
            "handler": handle_pair_device
        },
        {
            "name": "android_execute_tap",
            "description": "Tap at specified screen coordinates on paired Android device",
            "parameters": {
                "type": "object",
                "properties": {
                    "x": {"type": "number", "description": "X coordinate"},
                    "y": {"type": "number", "description": "Y coordinate"},
                    "duration_ms": {"type": "number", "default": 100, "description": "Press duration"}
                },
                "required": ["x", "y"]
            },
            "handler": handle_execute_tap
        },
        {
            "name": "android_type_text",
            "description": "Type text into active input field on paired Android device",
            "parameters": {
                "type": "object",
                "properties": {
                    "text": {"type": "string", "description": "Text to type"},
                    "device_id": {"type": "string", "optional": True, "description": "Target device ID (defaults to first paired)"}
                },
                "required": ["text"]
            },
            "handler": handle_type_text
        },
        {
            "name": "android_swipe_gesture",
            "description": "Perform swipe gesture on Android device screen",
            "parameters": {
                "type": "object",
                "properties": {
                    "start_x": {"type": "number"},
                    "start_y": {"type": "number"},
                    "end_x": {"type": "number"},
                    "end_y": {"type": "number"},
                    "duration_ms": {"type": "number", "default": 300}
                },
                "required": ["start_x", "start_y", "end_x", "end_y"]
            },
            "handler": handle_swipe_gesture
        },
        {
            "name": "android_get_screenshot",
            "description": "Capture screenshot from paired Android device",
            "parameters": {
                "type": "object",
                "properties": {
                    "device_id": {"type": "string", "optional": True}
                },
                "required": []
            },
            "handler": handle_get_screenshot
        },
        {
            "name": "android_read_ui_tree",
            "description": "Get accessibility tree structure from Android device",
            "parameters": {
                "type": "object",
                "properties": {
                    "device_id": {"type": "string", "optional": True},
                    "depth_limit": {"type": "integer", "default": 10}
                },
                "required": []
            },
            "handler": handle_read_ui_tree
        },
        {
            "name": "android_launch_app",
            "description": "Launch application on Android device by package name",
            "parameters": {
                "type": "object",
                "properties": {
                    "package_name": {"type": "string", "description": "App package name (e.g., com.android.chrome)"},
                    "device_id": {"type": "string", "optional": True}
                },
                "required": ["package_name"]
            },
            "handler": handle_launch_app
        }
    ]


async def handle_pair_device(params: dict, device_id: Optional[str] = None) -> dict:
    """Handler for android_pair_device tool"""
    
    display_name = params.get("display_name", f"Device_{secrets.token_hex(4)}")
    pairing_code = DeviceSession.generate_pairing_code()
    session_token = DeviceSession.generate_session_token()
    
    new_session = DeviceSession(
        device_id=str(uuid.uuid4()),
        pairing_code=pairing_code,
        session_token=session_token,
        connected_at=datetime.now(),
        permissions=[]
    )
    
    ws_manager.sessions[new_session.device_id] = new_session
    
    return {
        "status": "success",
        "device_id": new_session.device_id,
        "display_name": display_name,
        "pairing_code": pairing_code,
        "instructions": f"Open Android app and enter pairing code: {pairing_code}"
    }


async def handle_execute_tap(params: dict, device_id: Optional[str] = None) -> dict:
    """Handler for android_execute_tap tool"""
    
    x = params.get("x")
    y = params.get("y")
    duration_ms = params.get("duration_ms", 100)
    
    if not device_id:
        device_id = list(ws_manager.sessions.keys())[0] if ws_manager.sessions else None
    
    if not device_id or device_id not in ws_manager.sessions:
        return {"error": "No active device connection found"}
    
    command = {
        "action": "tap",
        "params": {"x": x, "y": y, "duration_ms": duration_ms},
        "timeout_ms": 5000
    }
    
    await ws_manager.broadcast_command(command, target_device_id=device_id)
    
    return {"status": "command_sent", "action": "tap", "coordinates": (x, y)}


async def handle_type_text(params: dict, device_id: Optional[str] = None) -> dict:
    """Handler for android_type_text tool"""
    
    text = params.get("text", "")
    
    if not device_id:
        device_id = list(ws_manager.sessions.keys())[0] if ws_manager.sessions else None
    
    if not device_id or device_id not in ws_manager.sessions:
        return {"error": "No active device connection found"}
    
    command = {
        "action": "type_text",
        "params": {"text": text},
        "timeout_ms": 10000
    }
    
    await ws_manager.broadcast_command(command, target_device_id=device_id)
    
    return {"status": "command_sent", "text_length": len(text)}


async def handle_swipe_gesture(params: dict, device_id: Optional[str] = None) -> dict:
    """Handler for android_swipe_gesture tool"""
    
    start_x = params.get("start_x")
    start_y = params.get("start_y")
    end_x = params.get("end_x")
    end_y = params.get("end_y")
    duration_ms = params.get("duration_ms", 300)
    
    if not device_id:
        device_id = list(ws_manager.sessions.keys())[0] if ws_manager.sessions else None
    
    if not device_id or device_id not in ws_manager.sessions:
        return {"error": "No active device connection found"}
    
    command = {
        "action": "swipe",
        "params": {
            "start_x": start_x,
            "start_y": start_y,
            "end_x": end_x,
            "end_y": end_y,
            "duration_ms": duration_ms
        },
        "timeout_ms": 5000
    }
    
    await ws_manager.broadcast_command(command, target_device_id=device_id)
    
    return {"status": "command_sent", "gesture": "swipe"}


async def handle_get_screenshot(params: dict, device_id: Optional[str] = None) -> dict:
    """Handler for android_get_screenshot tool"""
    
    if not device_id:
        device_id = list(ws_manager.sessions.keys())[0] if ws_manager.sessions else None
    
    if not device_id or device_id not in ws_manager.sessions:
        return {"error": "No active device connection found"}
    
    command = {
        "action": "get_screenshot",
        "params": {},
        "timeout_ms": 10000
    }
    
    await ws_manager.broadcast_command(command, target_device_id=device_id)
    
    return {"status": "command_sent", "action": "capture_screenshot"}


async def handle_read_ui_tree(params: dict, device_id: Optional[str] = None) -> dict:
    """Handler for android_read_ui_tree tool"""
    
    depth_limit = params.get("depth_limit", 10)
    
    if not device_id:
        device_id = list(ws_manager.sessions.keys())[0] if ws_manager.sessions else None
    
    if not device_id or device_id not in ws_manager.sessions:
        return {"error": "No active device connection found"}
    
    command = {
        "action": "read_ui_tree",
        "params": {"depth_limit": depth_limit},
        "timeout_ms": 15000
    }
    
    await ws_manager.broadcast_command(command, target_device_id=device_id)
    
    return {"status": "command_sent", "action": "read_accessibility_tree"}


async def handle_launch_app(params: dict, device_id: Optional[str] = None) -> dict:
    """Handler for android_launch_app tool"""
    
    package_name = params.get("package_name")
    
    if not device_id:
        device_id = list(ws_manager.sessions.keys())[0] if ws_manager.sessions else None
    
    if not device_id or device_id not in ws_manager.sessions:
        return {"error": "No active device connection found"}
    
    command = {
        "action": "launch_app",
        "params": {"package_name": package_name},
        "timeout_ms": 10000
    }
    
    await ws_manager.broadcast_command(command, target_device_id=device_id)
    
    return {"status": "command_sent", "package": package_name}


async def websocket_handler(request: web.Request) -> web.StreamResponse:
    """WebSocket endpoint for device connections"""
    
    ws = web.WebSocketResponse()
    await ws.prepare(request)
    
    print(f"New WebSocket connection from {request.remote}")
    
    async for msg in ws:
        if msg.type == WSMsgType.TEXT:
            try:
                data = json.loads(msg.data)
                logger.info(f"Received: {data.get('type', 'unknown')}")
                
                # Handle different message types
                if data.get("type") == "auth.request":
                    pairing_code = data.get("pairing_code")
                    
                    await ws_manager.authenticate_device(ws, pairing_code)
                
                elif data.get("type") == "command.complete":
                    request_id = data.get("request_id")
                    success = data.get("success", False)
                    result = data.get("result", {})
                    
                    await ws_manager.register_command_response(
                        "client", request_id, result, success
                    )
                
                elif data.get("type") == "command.error":
                    request_id = data.get("request_id")
                    error = data.get("error", "Unknown error")
                    
                    logger.error(f"Command failed: {request_id} - {error}")
            
            except json.JSONDecodeError:
                logger.error(f"Invalid JSON received: {msg.data}")
        
        elif msg.type == WSMsgType.ERROR:
            logger.error(f"WebSocket error: {ws.exception()}")
            break
    
    return ws


async def health_check(request: web.Request) -> web.Response:
    """Health check endpoint"""
    
    return web.json_response({
        "status": "healthy",
        "connected_devices": len(ws_manager.sessions),
        "uptime": "active"
    })


def create_app(port: int = 8765) -> web.Application:
    """Create and configure the web application"""
    
    app = web.Application()
    
    # Routes
    app.router.add_get('/', health_check)
    app.router.add_get('/ws', websocket_handler)
    app.router.add_post('/api/v1/tools/register', register_tools_endpoint)
    
    return app


async def register_tools_endpoint(request: web.Request) -> web.Response:
    """Endpoint to register all available tools with Hermes Agent"""
    
    tools = create_hermes_tools()
    
    return web.json_response({
        "status": "success",
        "tools_count": len(tools),
        "tools": tools
    })


async def main(host: str = "0.0.0.0", port: int = 8765):
    """Main entry point"""
    
    print("=" * 60)
    print("🚀 Hermes Android Bridge - Relay Server")
    print("=" * 60)
    print(f"Starting server on http://{host}:{port}")
    print(f"WebSocket endpoint: ws://{host}:{port}/ws")
    print(f"\nAvailable tools:")
    
    tools = create_hermes_tools()
    for tool in tools:
        print(f"  • {tool['name']}")
    
    print("\n" + "=" * 60)
    
    app = create_app(port)
    runner = web.AppRunner(app)
    await runner.setup()
    
    site = web.TCPSite(runner, host, port)
    await site.start()
    
    logger.info(f"Server started on port {port}")
    
    # Keep running
    while True:
        await asyncio.sleep(3600)


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="Hermes Android Bridge Relay Server")
    parser.add_argument("--host", default="0.0.0.0", help="Host to bind to")
    parser.add_argument("--port", type=int, default=8765, help="Port to bind to")
    
    args = parser.parse_args()
    
    asyncio.run(main(args.host, args.port))
