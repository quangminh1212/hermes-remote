#!/usr/bin/env python3
"""
Test script for Hermes Android Bridge API
This script demonstrates how to interact with the Android bridge from Python
"""

import asyncio
import websockets
import json
import time
from typing import Optional, Dict, Any

class AndroidBridgeTester:
    """Test client for Android Bridge WebSocket API"""
    
    def __init__(self, server_url: str = "ws://localhost:8765/ws"):
        self.server_url = server_url
        self.websocket: Optional[websockets.WebSocketClientProtocol] = None
        self.session_token: Optional[str] = None
        self.pairing_code: Optional[str] = None
        
    async def connect(self, pairing_code: str):
        """Connect to Android device and authenticate"""
        print(f"Connecting to {server_url}...")
        
        self.websocket = await websockets.connect(server_url)
        self.pairing_code = pairing_code
        
        # Send authentication request
        auth_request = {
            "type": "auth.request",
            "pairing_code": pairing_code
        }
        
        print(f"Sending authentication request with code: {pairing_code}")
        await self.websocket.send(json.dumps(auth_request))
        
        # Wait for auth response
        response = await self.websocket.recv()
        msg = json.loads(response)
        
        if msg.get("type") == "auth.grant":
            self.session_token = msg.get("session_token")
            print("✅ Authentication successful!")
            
            # Request permissions
            print("📋 Granted permissions:", msg.get("permissions", []))
            return True
            
        elif msg.get("type") == "auth.reject":
            print(f"❌ Authentication failed: {msg.get('reason')}")
            return False
        else:
            print(f"❌ Unexpected response: {msg}")
            return False
    
    async def execute_command(
        self, 
        action: str, 
        params: Optional[Dict[str, Any]] = None,
        timeout_ms: int = 5000
    ) -> Dict[str, Any]:
        """Execute a command on the Android device"""
        
        request_id = f"cmd_{int(time.time())}"
        
        command = {
            "type": "command.execute",
            "request_id": request_id,
            "action": action,
            "params": params or {},
            "timeout_ms": timeout_ms
        }
        
        print(f"\n📡 Sending command: {action} (ID: {request_id})")
        await self.websocket.send(json.dumps(command))
        
        # Wait for response
        try:
            response = await asyncio.wait_for(self.websocket.recv(), timeout=timeout_ms / 1000.0)
            msg = json.loads(response)
            
            if msg.get("type") == "command.complete":
                result = msg.get("result", {})
                success = msg.get("success", False)
                
                if success:
                    print(f"✅ Command completed successfully")
                    if "ui_tree_json" in result:
                        print(f"   📝 UI Tree contains {len(result['ui_tree_json'].get('children', []))} nodes")
                    return result
                else:
                    print(f"⚠️ Command returned success=false")
                    return result
                    
            elif msg.get("type") == "command.error":
                error = msg.get("error", "Unknown error")
                print(f"❌ Command failed: {error}")
                return {"error": error, "success": False}
                
            else:
                print(f"❌ Unexpected response: {msg}")
                return {"unexpected_response": msg, "success": False}
                
        except asyncio.TimeoutError:
            print(f"⏰ Command timed out after {timeout_ms}ms")
            return {"error": "Timeout", "success": False}
    
    async def tap(self, x: int, y: int, duration_ms: int = 100):
        """Tap at specific coordinates"""
        result = await self.execute_command(
            "tap",
            {"x": x, "y": y, "duration_ms": duration_ms}
        )
        return result
    
    async def type_text(self, text: str):
        """Type text into active input field"""
        result = await self.execute_command(
            "type_text",
            {"text": text}
        )
        return result
    
    async def swipe(self, start_x: int, start_y: int, end_x: int, end_y: int, duration_ms: int = 300):
        """Swipe from one point to another"""
        result = await self.execute_command(
            "swipe",
            {"start_x": start_x, "start_y": start_y, "end_x": end_x, "end_y": end_y, "duration_ms": duration_ms}
        )
        return result
    
    async def get_screenshot(self):
        """Get screenshot of current screen"""
        result = await self.execute_command("get_screenshot")
        return result
    
    async def read_ui_tree(self, depth_limit: int = 10):
        """Read accessibility tree structure"""
        result = await self.execute_command(
            "read_ui_tree",
            {"depth_limit": depth_limit}
        )
        return result
    
    async def launch_app(self, package_name: str):
        """Launch an application"""
        result = await self.execute_command(
            "launch_app",
            {"package_name": package_name}
        )
        return result
    
    async def press_key(self, key_code: int):
        """Press hardware key"""
        result = await self.execute_command(
            "press_key",
            {"key_code": key_code}
        )
        return result
    
    async def press_home(self):
        """Press home button"""
        result = await self.execute_command("press_home")
        return result
    
    async def press_back(self):
        """Press back button"""
        result = await self.execute_command("press_back")
        return result
    
    async def scroll(self, direction: str):
        """Scroll window"""
        result = await self.execute_command(
            "scroll",
            {"direction": direction}
        )
        return result
    
    async def clear_clipboard(self):
        """Clear clipboard contents"""
        result = await self.execute_command("clear_clipboard")
        return result
    
    async def get_device_info(self):
        """Get device information"""
        result = await self.execute_command("get_device_info")
        return result
    
    async def run_demo_sequence(self):
        """Run a demonstration sequence of commands"""
        print("\n" + "="*60)
        print("🎯 RUNNING DEMO SEQUENCE")
        print("="*60)
        
        # Get device info first
        print("\n📊 1. Getting device information...")
        device_info = await self.get_device_info()
        if device_info.get("success"):
            info = device_info.get("device_info", {})
            print(f"   Model: {info.get('model', 'Unknown')}")
            print(f"   Android Version: {info.get('androidVersion', 'Unknown')}")
            print(f"   Screen: {info.get('screenWidth', '?')}x{info.get('screenHeight', '?')}")
        
        # Read UI tree
        print("\n🔍 2. Reading current UI tree...")
        ui_tree = await self.read_ui_tree(depth_limit=5)
        if ui_tree.get("success"):
            tree = ui_tree.get("ui_tree_json", {})
            print(f"   Root node: {tree.get('className', 'Unknown')}")
            print(f"   Text content: {tree.get('text', '')[:100]}")
        
        # Simulate navigation
        print("\n🔄 3. Simulating tap at center...")
        # Note: Use appropriate coordinates based on device
        screen_width = device_info.get("device_info", {}).get("screenWidth", 800)
        screen_height = device_info.get("device_info", {}).get("screenHeight", 1200)
        mid_point = {"x": screen_width // 2, "y": screen_height // 2}
        
        await self.tap(mid_point["x"], mid_point["y"])
        
        # Scroll down
        print("\n📜 4. Scrolling down...")
        await self.scroll("down")
        
        # Type some text
        print("\n✍️ 5. Testing text input...")
        await self.type_text("Hermes Bridge Test")
        
        # Try to launch Chrome (optional)
        print("\n🚀 6. Attempting to launch Chrome...")
        await self.launch_app("com.android.chrome")
        
        # Home button to exit
        print("\n🏠 7. Pressing home button to exit...")
        await self.press_home()
        
        print("\n✅ Demo sequence completed!")
    
    async def disconnect(self):
        """Disconnect from device"""
        if self.websocket:
            await self.websocket.close()
            print("\n🔌 Disconnected from Android device")


async def main():
    """Main entry point"""
    import sys
    
    print("🔧 Hermes Android Bridge Test Client")
    print("-" * 60)
    
    # Get pairing code from command line or prompt
    if len(sys.argv) > 1:
        pairing_code = sys.argv[1]
    else:
        pairing_code = input("Enter pairing code: ").strip()
        if not pairing_code:
            print("❌ Pairing code is required")
            return
    
    # Connect and run demo
    tester = AndroidBridgeTester()
    
    try:
        # Connect and authenticate
        if await tester.connect(pairing_code):
            # Run demo sequence
            await tester.run_demo_sequence()
    except KeyboardInterrupt:
        print("\n\n⏹️  Interrupted by user")
    except Exception as e:
        print(f"\n❌ Error: {e}")
    finally:
        # Disconnect
        await tester.disconnect()


if __name__ == "__main__":
    asyncio.run(main())
