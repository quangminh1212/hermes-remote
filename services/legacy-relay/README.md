# Legacy Python relay — SUPERSEDED, reference only

> **Do not deploy this.** The current design has the phone talk directly to the
> Hermes gateway over HTTP/SSE (see `apps/android` and `packages/pairing-cli`).
> This relay is the original WebSocket pairing design and is no longer part of
> the working system.
>
> It is kept because the WebSocket protocol and its tests still document an
> earlier approach, and a future multi-device fan-out could reuse the session
> model. Nothing in the shipping app talks to it.

## What it was

A WebSocket relay that let the Hermes agent drive an Android device: the agent
sent gesture/UI-tree tool calls, the phone executed them through an
AccessibilityService and returned the result.

```
Hermes agent ──tool call──► relay :8765 ──WS──► Android device
                          ◄──result──
```

## Why it was replaced

- Required a relay process, a pairing code and an extra hop on every call.
- The AccessibilityService approach needed broad device permissions.
- The direct HTTP/SSE client is simpler, needs no accessibility access, and
  reuses the gateway's existing API.

## Files

| File | Purpose |
|---|---|
| `relay_server.py` | The relay itself (aiohttp WebSocket server) |
| `test_relay_server.py` | Relay protocol tests |
| `test_server.py` | Server bootstrapping tests |
| `test_android_bridge.py` | Bridge protocol tests |
| `requirements.txt` | `aiohttp` and friends |

## Running the tests

```bash
cd services/legacy-relay
python -m venv venv && . venv/bin/activate   # or venv\Scripts\activate on Windows
pip install -r requirements.txt
pip install pytest pytest-asyncio
pytest
```
