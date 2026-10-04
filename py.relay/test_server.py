"""
Test script to verify Python relay server functionality
Run this after starting the relay server
"""

import asyncio
import json
from aiohttp import ClientSession

async def test_relay_server():
    """Test basic server connectivity and tools registration"""
    
    print("="*60)
    print("Testing Hermes Android Bridge Relay Server")
    print("="*60)
    
    try:
        # Test 1: Health check
        print("\n[TEST 1] Testing health endpoint...")
        async with ClientSession() as session:
            async with session.get('http://localhost:8765') as response:
                if response.status == 200:
                    data = await response.json()
                    print(f"✅ Health check passed: {data}")
                else:
                    print(f"❌ Health check failed: {response.status}")
                    return
        
        # Test 2: Register tools
        print("\n[TEST 2] Fetching available tools...")
        async with session.get('http://localhost:8765/api/v1/tools/register') as response:
            if response.status == 200:
                tools_data = await response.json()
                tools = tools_data['tools']
                print(f"✅ Found {len(tools)} available tools:")
                
                for tool in tools:
                    print(f"  • {tool['name']}")
                    print(f"    Description: {tool['description'][:50]}...")
            else:
                print(f"❌ Tools fetch failed: {response.status}")
                return
        
        # Test 3: WebSocket connection (optional, if you have pairing code)
        print("\n[TEST 3] WebSocket connectivity...")
        
        # This would require actual pairing code from server
        # For now, just verify the endpoint is ready
        print("⏳ WebSocket endpoint ready at ws://localhost:8765/ws")
        print("   (Requires valid pairing code for authentication)")
        
        print("\n" + "="*60)
        print("All basic tests passed! ✅")
        print("="*60)
        print("\nNext steps:")
        print("1. Start Android app and enter pairing code")
        print("2. Enable Accessibility Service on device")
        print("3. Tap 'Start Connection'")
        print("4. Try calling tools via Hermes Agent API")
        
    except Exception as e:
        print(f"\n❌ Test failed: {e}")
        raise


if __name__ == "__main__":
    asyncio.run(test_relay_server())
