"""
Test suite for Hermes Android Bridge Relay Server
"""

import pytest
import asyncio
import json
from aiohttp import web, ClientSession
from relay_server import (
    WebSocketManager, 
    DeviceSession, 
    create_hermes_tools,
    create_app,
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
        """
        Valid coordinates are refused only because no device is attached.

        The old assertion expected `status == "command_sent"` while passing
        `device_id=None`, so it could not pass without a live phone.
        """
        params = {"x": 500, "y": 300}
        result = await handle_execute_tap(params, None)

        assert "error" in result
        assert "device" in result["error"].lower()

    @pytest.mark.asyncio
    async def test_handle_execute_tap_sends_when_device_attached(self):
        """With a session present the tap is forwarded and its coords echoed."""
        sent = {}

        class StubWs:
            async def send_json(self, payload):
                sent.update(payload)

        class StubSession:
            websocket = StubWs()

        manager = handle_execute_tap.__globals__["ws_manager"]
        sessions = manager.sessions
        original = dict(sessions)
        sessions.clear()
        sessions["dev-1"] = StubSession()
        # broadcast_command dispatches by session id; keep the stub in place.
        original_broadcast = manager.broadcast_command

        async def stub_broadcast(command, target_device_id=None):
            sent.update(command)

        manager.broadcast_command = stub_broadcast
        try:
            result = await handle_execute_tap({"x": 500, "y": 300}, "dev-1")
        finally:
            manager.broadcast_command = original_broadcast
            sessions.clear()
            sessions.update(original)

        assert result["status"] == "command_sent"
        # The handler reports coordinates as a tuple.
        assert result["coordinates"] == (500, 300)
        assert sent["params"]["x"] == 500
        assert sent["params"]["y"] == 300
    
    @pytest.mark.asyncio
    async def test_handle_execute_tap_missing_coordinates(self):
        params = {}
        result = await handle_execute_tap(params, None)
        
        assert "error" in result
    
    @pytest.mark.asyncio
    async def test_handle_type_text(self):
        """
        With no device connected the handler must refuse, not pretend to send.

        These used to assert `status == "command_sent"` while passing
        `device_id=None`, so they only "passed" on a machine that happened to
        have a live session — and failed everywhere else.
        """
        params = {"text": "Hello World"}
        result = await handle_type_text(params, None)

        assert "error" in result
        assert "device" in result["error"].lower()

    @pytest.mark.asyncio
    async def test_handle_type_text_length_is_reported_when_sent(self):
        """The sent reply carries the character count the old test expected."""
        # Drive the handler with a stub session so the send path is exercised
        # without a real phone attached.
        sent = {}

        class StubWs:
            async def send_json(self, payload):
                sent.update(payload)

        class StubSession:
            websocket = StubWs()

        original = handle_type_text.__globals__["ws_manager"].sessions
        handle_type_text.__globals__["ws_manager"].sessions = {"dev-1": StubSession()}
        try:
            result = await handle_type_text({"text": "Hello World"}, "dev-1")
        finally:
            handle_type_text.__globals__["ws_manager"].sessions = original

        assert result["status"] == "command_sent"
        assert result["text_length"] == 11

    @pytest.mark.asyncio
    async def test_handle_swipe_gesture(self):
        """A swipe is rejected when no device is connected."""
        params = {
            "start_x": 100,
            "start_y": 500,
            "end_x": 100,
            "end_y": 200
        }
        result = await handle_swipe_gesture(params, None)

        assert "error" in result


@pytest.mark.asyncio
async def test_websocket_connection():
    """
    A WebSocket client can connect and gets an auth answer.

    Uses the app's own runner rather than the `aiohttp_server` fixture, so the
    suite needs no extra pytest plugin. The previous version defined a
    `TestWebSocket` class it never instantiated and then bound a fixed port with
    an unimported `ClientSession`, so it could never pass on any machine.
    """
    app = create_app(8765)
    runner = web.AppRunner(app)
    await runner.setup()
    # Port 0 asks the OS for a free port, so parallel runs cannot collide.
    site = web.TCPSite(runner, "127.0.0.1", 0)
    await site.start()
    port = runner.addresses[0][1]

    try:
        async with ClientSession() as session:
            async with session.ws_connect(f"http://127.0.0.1:{port}/ws") as ws:
                await ws.send_json({"type": "auth.request", "pairing_code": "TEST123"})
                msg = await ws.receive_json()
    finally:
        await runner.cleanup()

    assert msg["type"] in ["auth.grant", "auth.reject"]


def test_error_handling():
    """Test various error scenarios"""
    
    # Test invalid pairing code handling
    # (Would need full mock setup)
    
    pass


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
