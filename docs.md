# Contributing to Hermes Android Bridge

## 🎯 Code of Conduct

This project is committed to providing a welcoming and inclusive environment. All contributors are expected to be respectful and professional.

---

## 🚀 Getting Started

### 1. Fork & Clone
```bash
git clone https://github.com/your-username/hermes-android-bridge.git
cd hermes-android-bridge
```

### 2. Install Dependencies

#### Python Server
```bash
cd py.relay
python -m venv venv
venv\Scripts\activate  # Windows
# source venv/bin/activate  # Linux/Mac
pip install -r requirements.txt
pip install pytest pytest-asyncio  # For testing
```

#### Android App
- Install Android Studio Arctic Fox+
- Open project: File → Open → Select `android-app` directory
- Wait for Gradle sync to complete

### 3. Set up Git Hooks (IMPORTANT)
```bash
cd C:\Dev\Hermes_Android

# Configure git to use hooks
git config core.hooksPath .githooks

# This enables:
# • Commit message validation (Conventional Commits)
# • Trailing whitespace removal
```

---

## 💻 Development Workflow

### Feature Branches
Use descriptive branch names:
```bash
git checkout -b feat/add-screenshot-capture
git checkout -b fix/websocket-reconnection-bug
git checkout -b docs/update-readme-instructions
```

### Conventional Commits ⚠️ REQUIRED

All commits MUST follow [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>(<scope>): <description>

[optional body]
[optional footer(s)]
```

#### Accepted Types

| Type | Description | Example Scope |
|------|-------------|---------------|
| `feat` | New feature | `feat/android-launch-app` |
| `fix` | Bug fix | `fix/ws-connection-timeout` |
| `docs` | Documentation changes | `docs/update-setup-guide` |
| `style` | Code style changes (formatting) | `style/cleanup-imports` |
| `refactor` | Refactoring without behavior change | `refactor/session-management` |
| `perf` | Performance improvements | `perf/ui-tree-speedup` |
| `test` | Adding/updating tests | `test/pairing-flow-integration` |
| `chore` | Maintenance tasks | `chore/update-dependencies` |

#### Valid Examples ✅

```
feat(android): add screenshot capture capability
feat(websocket): implement automatic reconnection logic

fix(server): resolve timeout on long-running sessions  
fix(ui): correct button alignment in dark mode

docs(readme): update installation instructions  
docs(api): add tool usage examples  

refactor(accessibility): extract gesture execution methods
refactor(architecture): simplify session management

test(relay): add pairing code validation tests
test(android): write UI tree reading unit tests

perf(tree-reading): optimize accessibility node traversal

chore(deps): bump okhttp dependency to v4.12.0
chore(ci): add GitHub Actions workflow
```

#### Invalid Examples ❌

```
Fixed bug  # Missing type
Updated readme  # Missing type
miscellaneous changes  # Invalid type
wip - never mind  # Improper formatting
```

### Pre-commit Checks

Git hooks automatically run these checks before every commit:

1. **Commit message validation**: Must follow Conventional Commits
2. **Trailing whitespace**: Removed automatically
3. **Syntax checks**: If applicable

If a check fails, fix the issue and retry the commit.

---

## 🔨 Making Changes

### Best Practices

#### Kotlin Guidelines (Android)
```kotlin
// Use explicit return types
fun processData(input: String): Map<String, Int> {
    return input.split(",").map { it.toInt() }
}

// Prefer immutable collections
val items: List<String> = listOf("a", "b", "c")  // ✅
var items: MutableList<String> = mutableListOf()  // Only if mutable needed

// Handle coroutines properly
suspend fun fetchData(): Result<Data> = try {
    val data = apiService.getData()
    Result.success(data)
} catch (e: Exception) {
    Result.failure(e)
}
```

#### Python Guidelines (Server)
```python
# Type hints required
def process_command(payload: dict, device_id: str) -> Optional[dict]:
    if not payload.get("action"):
        return None
    
    return execute_action(payload["action"], payload.get("params", {}))

# Async patterns
async def handle_websocket_message(ws: WebSocket, message: dict):
    try:
        result = await execute_command(message)
        await ws.send_json(result)
    except Exception as e:
        logger.error(f"Command failed: {e}")
        await ws.send_error("execution_failed")

# Logging
import logging
logger = logging.getLogger(__name__)

logger.info(f"Device paired: {device_id}")
logger.warning("Pairing attempt exceeded limit")
logger.error("WebSocket connection dropped unexpectedly")
```

### Testing Requirements

Every feature or bug fix should include appropriate tests:

```bash
# Run Python tests
pytest py.relay/test_relay_server.py -v

# Run Android tests
cd android-app
./gradlew :app:testDebugUnitTest
```

Test coverage targets:
- Python server: ≥70%
- Android client: ≥60%

---

## 🤝 Submitting Changes

### Pull Request Process

1. **Update documentation** - Update README, log, or relevant guides
2. **Run all tests** - Ensure everything passes locally
3. **Create PR** from feature branch to `main`
4. **Fill PR template** with:
   - Description of changes
   - Screenshots (for UI changes)
   - Testing performed
   - Links to related issues
5. **Await review** - Respond to feedback promptly
6. **Merge** - Once approved by maintainer

### PR Checklist

Before submitting PR:
- [ ] Code follows project conventions
- [ ] Self-review completed
- [ ] Tests added and passing
- [ ] Documentation updated
- [ ] No merge conflicts
- [ ] CI pipeline passes

---

## 🐛 Reporting Bugs

### How to Report

Provide this information:

1. **Summary**: Clear one-line description
2. **Steps to Reproduce**: Exact steps that trigger bug
3. **Expected Behavior**: What should happen
4. **Actual Behavior**: What actually happened
5. **Environment Details**:
   - Python version
   - Android version + device model
   - Network conditions
   - Relevant logs

### Example Bug Report

```markdown
**Bug**: Swipe gesture doesn't execute when duration_ms > 5000

**Steps to Reproduce**:
1. Connect device to server
2. Call android_swipe_gesture with duration_ms=6000
3. Observe command timeout after 5 seconds

**Expected**: Swipe executes successfully within configured time
**Actual**: Returns error "timeout exceeded"

**Environment**:
- Python: 3.11.4
- Android: Samsung Galaxy S21, API 32
- Network: WiFi 6, 20ms latency

**Logs**:
[ERROR] Command failed: req_12345 - timeout exceeded after 5000ms
```

---

## 💡 Suggesting Features

Feature requests welcome! Please provide:

1. **Problem Statement**: What problem does this solve?
2. **Proposed Solution**: High-level approach
3. **Benefits**: Who benefits and how
4. **Alternatives Considered**: Other approaches evaluated
5. **Additional Context**: Screenshots, mockups, etc.

### Feature Request Template

```markdown
**Feature**: Add voice command support

**Problem Statement**:
Users cannot currently control Android via voice commands through Hermes Agent.

**Proposed Solution**:
Integrate speech-to-text service, map recognized phrases to Android actions.

**Benefits**:
- Hands-free operation
- Accessibility improvements
- Expanded automation scenarios

**Alternatives**:
- External TTS integration (rejected - adds complexity)
- Manual text entry (too slow for repeated actions)

**Mockup**:
[Attach screenshot or diagram if available]
```

---

## 📚 Learning Resources

### Architecture Overview
See `PROJECT_OVERVIEW.md` for system design details

### Key Technologies
- **Kotlin**: Official Android development language
- **Jetpack Compose**: Modern declarative UI toolkit
- **OkHttp**: HTTP client library
- **aiohttp**: Async HTTP server for Python
- **WebSocket**: Real-time bidirectional communication

### Useful Documentation
- [Accessibility Service Guide](https://developer.android.com/guide/topics/ui/accessibility/service)
- [Jetpack Compose Basics](https://developer.android.com/jetpack/compose/tutorial)
- [WebSocket RFC 6455](https://datatracker.ietf.org/doc/html/rfc6455)
- [Python AsyncIO Best Practices](https://docs.python.org/3/library/asyncio.html)

---

## 🤲 Community Expectations

### Be Professional
- Use welcoming and inclusive language
- Provide constructive feedback
- Acknowledge others' contributions
- Accept critical comments gracefully

### Be Collaborative
- Share knowledge freely
- Help troubleshoot issues
- Review pull requests timely
- Document thoroughly

### Be Transparent
- Communicate delays early
- Ask for help when stuck
- Document decision rationale
- Keep changelog up to date

---

## 📞 Contact

- **GitHub Issues**: Report bugs/suggestions here
- **Discussions**: General questions and community chat
- **Email**: contact@hermesbridge.dev (response within 48h)

---

**Thank you for contributing! Your efforts make this project better for everyone.** 🙏

*Last updated: January 2025*
