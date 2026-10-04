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

### 3. Auto-update APK (tự lên bản) — MỚI
- **Cơ chế:** app tự kiểm tra GitHub Releases khi mở app (rate-limit **6h/lần**, lưu mốc ở SharedPreferences `hermes_bridge_update`). Nếu có bản `versionCode` lớn hơn → hiện **banner "Có bản cập nhật X"** ở đầu màn hình chat với 2 nút *Để sau* / *Cập nhật*.
- **Cách áp dụng:** bấm *Cập nhật* → tải APK vào `cacheDir/updates/` (hiện thanh tiến trình %) → mở trình cài hệ thống qua **FileProvider** `com.hermes.bridge.fileprovider` (paths ở `res/xml/file_paths.xml`). Cần quyền **`REQUEST_INSTALL_PACKAGES`** + người dùng bật *"Cho phép cài từ nguồn này"* (app tự mở màn Settings tương ứng nếu thiếu).
- **Nguồn:** `UpdateSource.kt` trỏ `quangminh1212/hermes-remote` (PUBLIC — repo private thì GitHub API 404 vì app không có token).
- **⚠️ QUY ƯỚC ĐÁNH VERSION (BẮT BUỘC):** GitHub KHÔNG gửi `versionCode` → phải nhét vào **tag** dạng **`v<versionName>+<versionCode>`**, ví dụ **`v1.0.0+2`**. App parse `+<số>` từ tag (fallback: tên asset `hermes-<versionName>+<versionCode>.apk`). **Mỗi lần phát hành: bump `versionCode` trong `app/build.gradle.kts`, build `assembleRelease`, tạo GitHub Release với tag đúng dạng và đính kèm `app-release.apk`.**
- **Code:** `updater/UpdateInfo.kt` (model + parse GitHub JSON/tag), `updater/UpdateChecker.kt` (GET `/releases/latest`, so versionCode), `updater/ApkDownloader.kt` (tải + tiến trình), `updater/UpdateInstaller.kt` (FileProvider + intent cài). UI ở `MainActivity.UpdateBanner`, state ở `MainViewModel` (`UpdateUiState`, `checkForUpdate`, `downloadUpdate`, `dismissUpdate`).
- **Test:** `UpdateInfoTest` (6) + `UpdateCheckerTest` (6, MockWebServer) → tổng **37/37 PASS** (25 cũ + 12 mới). Verify APK release có quyền `REQUEST_INSTALL_PACKAGES` + provider.
- **KHÔNG tự cài ngầm:** cố ý chỉ tải + mở trình cài, để hệ thống/người dùng xác nhận (an toàn, tránh bị chặn).

## Đã verify trên giả lập (máy này)
- Emulator `aosp_atd` (Android 11 / API 30), AVD `hermes_test`, boot ~35s (`-no-snapshot-load -no-boot-anim -gpu swiftshader_indirect`).
- Cài APK → app chạy, `MainActivity` resumed, **không crash/ANR**. UI xác nhận qua `uiautomator dump` (đầy đủ: header `Model: Claude-Fable-5.3`, nút 📷 Quét QR, 4 quick buttons, ô nhập "Nhắn lệnh cho Hermes…", nút Gửi, ⚙, Xoá).
- Nút **"Quét QR"** → mở đúng `barcodescanner.CaptureActivity` + xin quyền CAMERA.
- **Vòng khép kín CHẠY THẬT (session 2):** nạp config vào SharedPreferences (base_url=`http://10.0.2.2:8642`, key từ `.env`, model `glm-5.3`) → kích hoạt nút **"Git status"** → app gửi *"Cho tôi biết trạng thái git hiện tại của dự án đang làm việc."* → Hermes stream trả lời *"Tôi cần kiểm tra git status thực tế để đưa ra câu trả lời chính xác cho bạn."* → app hiển thị. **app → gateway(8642) → agent → stream về app: OK.**

## Việc CÒN LẠI (chưa làm)
1. **Cài `app-release.apk` lên điện thoại Android thật** + quét QR thật đầu-cuối (emulator `aosp_atd` không có camera → chưa test quét QR bằng mắt). APK release đã build + ký sẵn. **Auto-update cũng cần xác nhận cú bấm "Cập nhật" trên máy thật** (emulator không cài được qua UI).
2. (Tùy chọn) Publish npm pack lên registry thật.
3. Kiểm tra `/api/connect` khớp `ConnectPayload.kt` khi có profile Hermes đặt tên (hiện `profile` để trống dùng key top-level `.env`). Profiles đang có: `dalek`, `doraemon`, `heimeringer`.
4. ~~SỬA BUG LAYOUT nút "Git status" bị cắt cụt~~ → **ĐÃ SỬA (session 3):** thực ra row cũ đã có `horizontalScroll` (nút cuối cuộn tới được, chỉ là UI dump báo bounds ngoài màn hình — giống bug harness `jks:20`). Đổi sang **`FlowRow`** để nút tự xuống dòng, không nút nào bị khuất. Đã build lại OK.
5. ~~Bỏ `"web/"` khỏi `files`~~ → **ĐÃ XONG (session 3):** bỏ khỏi `package.json`, xóa luôn thư mục `npm-pack/web/` rỗng. `npm pack --dry-run` = 7 files, 9.3 kB.
6. ~~`npm test` treo/không chạy~~ → **ĐÃ SỬA (session 3):** `"test": "node --test test/"` fail `MODULE_NOT_FOUND` trên Node 26 (nó coi arg là module, không phải dir). Đổi sang `node --test test/*.test.js` → **5/5 PASS** (`test/pack.test.js` đã có sẵn từ trước nhưng chưa từng chạy được).

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

## Trạng thái git (session 3)
- **ĐÃ CÓ REMOTE:** `origin` = `https://github.com/quangminh1212/hermes-remote.git` (PUBLIC) → push được.
- Commit mới nhất (mới nhất ở trên):
  - `feat(android): add self-update via GitHub Releases` (banner + tải + cài, 12 test mới)
  - `6c4fffc` feat(npm-pack): add hermes-remote pairing CLI
  - `f603926` feat(android): rewrite app as QR-paired HTTP/SSE chat client
  - `7d69579` chore(gitignore): stop tracking keystore.properties
  - `02c4db7` refactor: rename files/folders to dotted convention
- Working tree **SẠCH**. File ngoài git: `keystore.properties`, `C:\Users\GHC\hermes-keys\*` (KHÔNG bao giờ commit).
- **Phát hành bản mới cho auto-update:** bump `versionCode` trong `app/build.gradle.kts` → `assembleRelease` → `gh release create v<versionName>+<versionCode> app-release.apk`.
