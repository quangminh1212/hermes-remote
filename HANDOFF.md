# BÀN GIAO — Hermes Android Remote

> Dán cả file này (hoặc nội dung dưới) vào **session Hermes mới** để tiếp tục.

## Trạng thái: GẦN XONG — 2 pack đã build & test thật

### 1. npm pack — `hermes-remote` (PC)
- **Đường dẫn:** `C:\Dev\Hermes_Android\npm-pack\`
- **Chạy:** `npx hermes-remote` (hoặc `npm install -g` từ tarball)
- **Việc nó làm:** đọc `.env` Hermes → ensure gateway chạy → mở **Cloudflare quick tunnel** (mặc định) → dựng webview trang QR → mở browser PC.
- **Payload QR:** `{v, baseUrl, fallbackUrl, apiKey, profile, model, profiles}` phục vụ tại `GET /api/connect`; QR PNG tại `/qr.png`; QR ASCII in ra terminal.
- **Đã test thật:** LAN mode OK; tunnel thật OK (public URL `/health` → 200, `/v1/models` → model thật); `npm pack` + `npm install -g --prefix <tmp>` + chạy binary OK. `node --test` → **5/5 PASS**.
- **Cờ:** `--tunnel` (mặc định) | `--no-tunnel`/`--lan` | `--port <n>` | `--no-open` | `-h`.
- **Đã verify lại (session 2):** `node bin/cli.js --lan --no-open --port 8899` → `/api/connect` 200 (payload đúng), `/` 200 (trang QR), `/qr.png` 200 (image/png ~6KB). Gateway Hermes thật đang chạy cổng **8642**.
- **CHÚ Ý:** `package.json` liệt `web/` trong `files` nhưng thư mục `web/` **RỖNG** (HTML render inline trong `lib/server.js`) → nên bỏ `"web/"` khỏi `files`.

### 2. Android app (điện thoại)
- **Đường dẫn:** `C:\Dev\Hermes_Android\android-app\`
- **Tính năng:** app chat remote → gửi lệnh cho Hermes trên PC. Nút **"Quét QR"** → quét QR của npm pack → tự fill config (baseUrl/apiKey/profile/model) → tự kết nối.
- **APK debug:** `app\build\outputs\apk\debug\app-debug.apk` — **14.23 MB**, package `com.hermes.bridge.debug`.
- **APK release (ĐÃ KÝ):** `app\build\outputs\apk\release\app-release.apk` — **2.14 MB**, package `com.hermes.bridge` (không suffix), label "Hermes Remote". R8 minify + shrinkResources bật. Verify chữ ký OK: `CN=Hermes Remote`, SHA-256 `f4fb5b41…7420`.
- **Ký release:** đọc `android-app/keystore.properties` (đã ignore trong git). Keystore ở **`C:\Users\GHC\hermes-keys\hermes-release.jks`** (alias `hermes`, mật khẩu ở `C:\Users\GHC\hermes-keys\keystore.pass` — cả 2 NGOÀI repo, KHÔNG commit). Nếu thiếu `keystore.properties`, release build tự fallback sang **unsigned** (không lỗi).
- **Test:** `gradlew assembleRelease` + `assembleDebug` + `testDebugUnitTest` → **BUILD SUCCESSFUL**, 25/25 test PASS. Env build: `JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot` — truyền dạng đường dẫn **Windows**, KHÔNG dùng `/c/...`.
- **Config app lưu ở:** SharedPreferences **`hermes_bridge_config`** (`base_url`, `api_key`, `profile`, `model`) — xem `data/ConfigStore.kt`. Default base URL `http://192.168.1.113:8642`, default model `glm-5.3`.

## Đã verify trên giả lập (máy này)
- Emulator `aosp_atd` (Android 11 / API 30), AVD `hermes_test`, boot ~35s (`-no-snapshot-load -no-boot-anim -gpu swiftshader_indirect`).
- Cài APK → app chạy, `MainActivity` resumed, **không crash/ANR**. UI xác nhận qua `uiautomator dump` (đầy đủ: header `Model: Claude-Fable-5.3`, nút 📷 Quét QR, 4 quick buttons, ô nhập "Nhắn lệnh cho Hermes…", nút Gửi, ⚙, Xoá).
- Nút **"Quét QR"** → mở đúng `barcodescanner.CaptureActivity` + xin quyền CAMERA.
- **Vòng khép kín CHẠY THẬT (session 2):** nạp config vào SharedPreferences (base_url=`http://10.0.2.2:8642`, key từ `.env`, model `glm-5.3`) → kích hoạt nút **"Git status"** → app gửi *"Cho tôi biết trạng thái git hiện tại của dự án đang làm việc."* → Hermes stream trả lời *"Tôi cần kiểm tra git status thực tế để đưa ra câu trả lời chính xác cho bạn."* → app hiển thị. **app → gateway(8642) → agent → stream về app: OK.**

## Việc CÒN LẠI (chưa làm)
1. **Cài `app-release.apk` lên điện thoại Android thật** + quét QR thật đầu-cuối (emulator `aosp_atd` không có camera → chưa test quét QR bằng mắt). APK release đã build + ký sẵn.
2. (Tùy chọn) Publish npm pack lên registry thật.
3. Kiểm tra `/api/connect` khớp `ConnectPayload.kt` khi có profile Hermes đặt tên (hiện `profile` để trống dùng key top-level `.env`). Profiles đang có: `dalek`, `doraemon`, `heimeringer`.
4. **SỬA BUG LAYOUT (mới phát hiện):** hàng 4 nút quick-action bị tràn màn hình 1080px — nút **"Git status" bị cắt cụt**, bounds chỉ còn `[1033,1977]-[1080,2072]` (47px, sát mép). Bốn nút: `[33..381] [403..736] [758..1011] [1033..1080]`. Cần cho row wrap hoặc chia đều (bố cục 2x2 / weight) để nút cuối không bị cắt.
5. Bỏ `"web/"` khỏi `files` trong `npm-pack/package.json` (thư mục rỗng).

## Ghi chú kỹ thuật quan trọng
- **ANTI-HANG:** KHÔNG restart gateway/bridge/headless từ trong chat → dùng process nền tách rời + poll kiểm chứng bên ngoài.
- **PowerShell:** phải ghi script ra file `.ps1` tạm rồi chạy `-File`; shell nuốt `$env:` trong one-liner (cả bash).
- **API key:** đọc từ `%LOCALAPPDATA%\hermes\.env` (biến `API_SERVER_KEY`, dài 64). KHÔNG in ra, KHÔNG lưu vào code/repo. Key này CÓ xuất hiện trong `/api/connect` (cố ý, app cần) → đừng log nó.
- **Shell ở đây là MSYS/Git Bash** (không phải WSL, không phải PowerShell). Đường dẫn Windows = `/c/Dev/...`. **Binary `.exe` của Windows (`adb.exe`, `gradlew.bat`, `emulator.exe`) KHÔNG hiểu `/c/...`** → phải truyền dạng `C:\...` (viết `"C:\\Dev\\..."` trong bash). `java`/`node`/`git` (MSYS-native) thì hiểu `/c/...`.
- **EMULATOR `aosp_atd` — 2 hạn chế quan trọng:**
  1. `adb shell screencap` luôn cho **ảnh ĐEN** (~15197 bytes cố định) — ATD không render framebuffer. Muốn "thấy" UI: `uiautomator dump` rồi đọc cây text, KHÔNG dựa vào ảnh.
  2. `adb shell input tap X Y` **KHÔNG kích hoạt được nút** (bị nuốt). Phải dùng **`adb shell input motionevent DOWN X Y`** + `sleep 0.2` + **`input motionevent UP X Y`** → mới nhận.
- **Nạp config app không cần UI:** `adb push` file prefs XML → `adb shell run-as com.hermes.bridge.debug cp /data/local/tmp/x.xml /data/data/com.hermes.bridge.debug/shared_prefs/hermes_bridge_config.xml`. Từ emulator tới host phải dùng IP **`10.0.2.2`** (không phải LAN IP).
- **JDK:** `C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot` (java trên PATH đã là 17.0.18). **Android SDK:** `C:\Users\GHC\AndroidSdk`. **ADB:** `C:\Users\GHC\AndroidSdk\platform-tools\adb.exe`.
- **Skill đã lưu:** `qr-pairing-npm-android`, `android-emulator-lightweight-test`.
- **Đổi tên file/folder theo quy tắc DẤU CHẤM** (1-2 từ cách nhau bằng `.`): **ĐÃ COMMIT** trong `02c4db7` — `CONTRIBUTING.md→docs.md`, `CHANGELOG.md→log.md`, `python-relay-server/→py.relay/` (kèm cập nhật path trong `docs.md`, `start_server.sh/.bat`).
- **Đã dọn:** xoá file rác `android-app/real` (log lỡ ghi thành file) và `android-app/NUL` (rác Windows).

## Trạng thái git (session 2)
- **KHÔNG có remote** (`git remote -v` rỗng) → **KHÔNG push được**; mọi commit chỉ nằm ở local.
- Commit mới nhất (mới nhất ở trên):
  - `6c4fffc` feat(npm-pack): add hermes-remote pairing CLI
  - `f603926` feat(android): rewrite app as QR-paired HTTP/SSE chat client
  - `7d69579` chore(gitignore): stop tracking keystore.properties
  - `02c4db7` refactor: rename files/folders to dotted convention
- Working tree **SẠCH** sau các commit theo từng phần (rename · gitignore · android rewrite · npm-pack). Chỉ còn file ngoài git: `keystore.properties`, `HANDOFF.md` (xem mục dưới).
- **Chưa đưa vào commit:** `HANDOFF.md` (tài liệu bàn giao — commit cùng phần doc nếu muốn).
- `keystore.properties` + `C:\Users\GHC\hermes-keys\*` **KHÔNG bao giờ commit** (đã ignore).
