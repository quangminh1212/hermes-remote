"""
Test suite for Hermes Android Bridge Relay Server
"""

import pytest
import asyncio
import json
from aiohttp import web
from relay_server import (
    WebSocketManager, 
    DeviceSession, 
    create_hermes_tools,
    handle_execute_tap,
    handle_type_text,
    handle_swipe_gesture
)


class TestDeviceSession:
    """Test DeviceSession class"""
    
    def test_generate_pairing_code(self):
        code1 = DeviceSession.generate_pairing_code()
        code2 = DeviceSession.generate_pairing_code()
        
        assert len(code1) == 6
        assert code1.isalnum()
        assert code1 != code2  # Should be different
    
    def test_generate_session_token(self):
        token1 = DeviceSession.generate_session_token()
        token2 = DeviceSession.generate_session_token()
        
        assert len(token1) > 32
        assert token1 != token2


class TestWebSocketManager:
    """Test WebSocketManager class"""
    
    @pytest.fixture
    def manager(self):
        return WebSocketManager()
    
    def test_initial_state(self, manager):
        assert len(manager.sessions) == 0
        assert len(manager.websocket_connections) == 0
    
    def test_authenticate_device_success(self, manager):
        # This would require mocking WebSocketResponse
        pass
    
    def test_command_broadcast(self, manager):
        # Mock setup required
        pass


class TestHermesTools:
    """Test tool creation and validation"""
    
    def test_create_hermes_tools(self):
        tools = create_hermes_tools()
        
        assert isinstance(tools, list)
        assert len(tools) > 5
        
        for tool in tools:
            assert "name" in tool
            assert "description" in tool
            assert "parameters" in tool
    
    def test_tool_parameters(self):
        tools = create_hermes_tools()
        tap_tool = next(t for t in tools if t['name'] == 'android_execute_tap')
        
        params = tap_tool['parameters']
        assert params['properties']['x']['type'] == 'number'
        assert params['properties']['y']['type'] == 'number'


class TestToolHandlers:
    """Test individual tool handlers"""
    
    @pytest.mark.asyncio
    async def test_handle_execute_tap_valid_coordinates(self):
        params = {"x": 500, "y": 300}
        result = await handle_execute_tap(params, None)
        
        assert "status" in result
        assert result["status"] == "command_sent"
    
    @pytest.mark.asyncio
    async def test_handle_execute_tap_missing_coordinates(self):
        params = {}
        result = await handle_execute_tap(params, None)
        
        assert "error" in result
    
    @pytest.mark.asyncio
    async def test_handle_type_text(self):
        params = {"text": "Hello World"}
        result = await handle_type_text(params, None)
        
        assert result["status"] == "command_sent"
        assert result["text_length"] == 11
    
    @pytest.mark.asyncio
    async def test_handle_swipe_gesture(self):
        params = {
            "start_x": 100,
            "start_y": 500,
            "end_x": 100,
            "end_y": 200
        }
        result = await handle_swipe_gesture(params, None)
        
        assert result["status"] == "command_sent"
        assert result["gesture"] == "swipe"


@pytest.mark.asyncio
async def test_websocket_connection():
    """Test basic WebSocket connection flow"""
    from aiohttp import web
    from aiohttp.test_utils import AioHTTPTestCase, unittest_run_loop
    
    class TestWebSocket(AioHTTPTestCase):
        @unittest_run_loop
        async def test_websocket_handshake(self):
            ws = await self.client.ws_connect('/ws')
            
            # Send auth request
            await ws.send_json({
                "type": "auth.request",
                "pairing_code": "TEST123"
            })
            
            # Receive response
            msg = await ws.receive_json()
            assert msg["type"] in ["auth.grant", "auth.reject"]
    
    app = create_app(8765)
    runner = web.AppRunner(app)
    await runner.setup()
    
    site = web.TCPSite(runner, 'localhost', 8766)
    await site.start()
    
    try:
        async with ClientSession() as session:
            async with session.ws_connect('http://localhost:8766/ws') as ws:
                await ws.send_json({"type": "test"})
    finally:
        await runner.cleanup()


def test_error_handling():
    """Test various error scenarios"""
    
    # Test invalid pairing code handling
    # (Would need full mock setup)
    
    pass


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
