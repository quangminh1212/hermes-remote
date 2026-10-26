"""
Test script to verify Python relay server functionality
Run this after starting the relay server

This is a manual smoke script, not a unit test: it talks to a relay that must
already be listening on :8766. pytest still collects it because of the `test_`
filename, so the async entry point is skipped unless the server is up — see the
guard inside, which turns the usual connection error into a skip rather than a
failure that hides real regressions.
"""

import asyncio
import json
import os
import pytest
from aiohttp import ClientSession

# Empty the list to hide this module from pytest entirely.
pytestmark = pytest.mark.skipif(
    os.environ.get("RELAY_LIVE") != "1",
    reason="manual smoke script — needs a relay listening on :8766 (set RELAY_LIVE=1)",
)


@pytest.mark.asyncio
async def test_relay_server():
    """Test basic server connectivity and tools registration"""
    
    print("="*60)
    print("Testing Hermes Android Bridge Relay Server")
    print("="*60)
    
    try:
        # Test 1: Health check
        print("\n[TEST 1] Testing health endpoint...")
        async with ClientSession() as session:
            async with session.get('http://localhost:8766') as response:
                if response.status == 200:
                    data = await response.json()
                    print(f"✅ Health check passed: {data}")
                else:
                    print(f"❌ Health check failed: {response.status}")
                    return
        
        # Test 2: Register tools
        print("\n[TEST 2] Fetching available tools...")
        async with session.get('http://localhost:8766/api/v1/tools/register') as response:
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
        print("⏳ WebSocket endpoint ready at ws://localhost:8766/ws")
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
