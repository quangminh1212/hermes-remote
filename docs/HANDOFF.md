# BÀN GIAO — Hermes Remote

> Dán cả file này (hoặc nội dung dưới) vào **session mới** để tiếp tục.

## Trạng thái: GẦN XONG — đã tái cấu trúc theo chuẩn quốc tế (session 5)

### 0.9. TÁI CẤU TRÚC + ĐỔI PACKAGE + UI XLAB (session 5)

**Bố cục mới (monorepo chuẩn quốc tế):**
```
apps/android/            ← app Android (trước: android-app/)
packages/pairing-cli/    ← npx hermes-remote (trước: npm-pack/)
services/legacy-relay/   ← relay Python CŨ, đã bỏ (trước: py.relay/)
scripts/                 ← mock-assistant.js, start-relay.sh/.bat
docs/                    ← HANDOFF, CONTRIBUTING, CHANGELOG, SECURITY
README.md                ← MỚI (trước đây không có)
```
Dùng `git mv` / Move-Item nên **git vẫn nhận diện rename**, lịch sử không mất.

**ĐỔI PACKAGE (theo yêu cầu, đã biết là phá auto-update):**
- `com.hermes.bridge` → **`io.hermes.remote`** (Java package, applicationId, namespace)
- File: `HermesBridgeApplication.kt` → `HermesRemoteApplication.kt`
- Theme: `Theme.HermesBridge` → `Theme.HermesRemote`
- Action: `com.hermes.bridge.action.ASK` → `io.hermes.remote.action.ASK`
  - **VẪN NHẬN action cũ** (manifest khai cả hai) → App Action đã đăng ký trước đó không gãy.
- **Chữ ký release GIỮ NGUYÊN** `f4fb5b41…7420` (dùng lại keystore cũ).
- ⚠️ App cũ trên máy **phải gỡ tay** (applicationId khác = app khác). Đã ghi ở `docs/SECURITY.md` + README.

**UI theo XLab_Web (`C:\Dev\XLab_Web`):** lấy design tokens từ `tailwind.config.js`.
- `ui/theme/XLabTheme.kt` — palette XLab (teal `#00A19A`), dark-only, Material3 scheme.
- `res/values/colors.xml` + `themes.xml` — cùng token, window background + system bars.
- `packages/pairing-cli/lib/pairing-page.js` — trang QR viết lại theo XLab (tách khỏi `server.js`).
- Cả 3 nơi dùng CÙNG token; có test chặn lệch màu.

**API KEY Ở ĐÂU** — đã viết `docs/SECURITY.md`:
1. Nguồn: `%LOCALAPPDATA%\hermes\.env` → biến `API_SERVER_KEY` (64 ký tự).
2. Tạm thời: payload `/api/connect` + ảnh QR (server chỉ bind `127.0.0.1`).
3. Trên máy: `shared_prefs/hermes_bridge_config.xml` (app-private, **không** mã hoá — có ghi lý do).
4. Trong repo: **KHÔNG CÓ**. `.gitignore` chặn `.env`, `keystore.properties`, `*.jks`.

**TỰ GIẢ LẬP TRỢ LÝ GOOGLE — `scripts/mock-assistant.js` (MỚI):**
Giả lập đúng những intent mà App Actions gửi, bắn vào APK thật qua adb. Chạy được khi KHÔNG có Play Services.
```
node scripts/mock-assistant.js --list
node scripts/mock-assistant.js --all      → 7/7 PASS
```
7 dạng: `custom-extra`, `assistant-text`, `bii-bundle`, `deep-link`, `voice-command`, `bare-invoke`, `hot-start`.
Harness **tự bắt được 2 bug thật**:
- **Bug app:** BII gửi tham số dạng **String** dưới key `parameters` bị bỏ qua (app chỉ tìm Bundle) → đã sửa.
- **Bug harness:** thứ tự đối số `am start` (package phải đứng SAU `--es`) + Unicode tiếng Việt bị shell cắt → đã sửa (push script qua `adb push`, base64).

**Kết quả test cuối (session 5):**
| Hạng mục | Kết quả |
|---|---|
| Android unit tests | **69/69 PASS** |
| Pairing CLI tests | **24/24 PASS** (15 cũ + 9 mới `pairing-page.test.js`) |
| Mock Assistant (emulator thật) | **7/7 PASS** |
| `assembleDebug` | OK — 12.12 MB, `io.hermes.remote.debug` |
| `assembleRelease` | OK — 4.01 MB, `io.hermes.remote` |
| Chữ ký release | **giữ nguyên** `f4fb5b41…7420` |
| Pairing page live | 200, XLab teal có mặt, QR 200 image/png |

### 0.5. TÍCH HỢP TRỢ LÝ GOOGLE — ĐÃ XONG & TEST THẬT (session 4)

> Yêu cầu: "tích hợp app android này vào trợ lý google, để nói chuyện với trợ lý google là Hermes PC tự nhận được".

**KẾT LUẬN ĐÃ KIỂM CHỨNG BẰNG NGUỒN GOOGLE (tháng 9/2026):**

| Câu hỏi | Sự thật | Nguồn |
|---|---|---|
| App có *thay não* "Hey Google" được không? | **KHÔNG.** Conversational Actions khai tử 2023. App chỉ có thể được Trợ lý **gọi tới**. | Google đã thông báo 2023 |
| App Actions còn sống không? | **CÒN.** Trang `developer.android.com/guide/app-actions/overview` cập nhật **2026-07-16**, mô tả `<capability>` trong `shortcuts.xml` là cách hiện hành. | fetch trực tiếp |
| AppFunctions (API mới) dùng được chưa? | **CHƯA.** Cần **API 36** + **Early Access Program invite**; "only a limited number of apps and system agents can access the entire pipeline". Cập nhật 2026-09-22. | `developer.android.com/ai/appfunctions` |
| Trợ lý Google có bị khai tử? | **CÓ** — đang chuyển dần sang **Gemini** (hoàn tất ~9/2026). Nhưng App Actions vẫn là kênh tích hợp, và Claude-Fable vẫn đọc `shortcuts.xml`. | TechRepublic, 9to5google |

→ **Đã làm đúng kênh còn sống: App Actions + `shortcuts.xml`.**

**3 LỖI THẬT ĐÃ SỬA:**

1. **BUG NẶNG — intent bị nuốt khi app đang mở.** `launchMode=singleTask` → intent thứ 2 đi vào `onNewIntent`. Code cũ đọc `query` nhưng **payload BII thật của Trợ lý không nằm ở key đó** → câu lệnh rơi mất im lặng. Đã viết `AssistantIntent` xử lý **4 dạng giao hàng** (xem bảng dưới).
2. **`shortcuts.xml` sai — free text gắn vào BII không chở được câu.** `actions.intent.OPEN_APP_FEATURE` có tham số `feature` là ***entity*** tra trong catalog cố định của app, **không** mang được câu bất kỳ. Đây chính là lý do "hey google ask <app> ..." im lặng. Đã đổi sang **`custom.hermes.ASK`** (custom capability, `required=true`); giữ `OPEN_APP_FEATURE` ở dạng `required=false` cho "open Hermes" trần.
3. **Gửi trùng khi xoay màn hình / process restore.** `onCreate` chạy lại → gửi lại prompt + đọc lại toàn bộ. Đã chặn bằng `savedInstanceState == null`.

**Thêm mới:** `VOICE_COMMAND` intent-filter + deep link `hermes://ask?query=…` + `sendFromAssistant()` (lượt do Trợ lý gọi được **đọc to** như lượt nói) + banner UI "🎤 Từ Trợ lý Google" + mục hướng dẫn trong ⚙ Cài đặt.

**4 DẠNG GIAO HÀNG — ĐÃ TEST THẬT 100% TRÊN EMULATOR:**

| Dạng | Lệnh adb | Kết quả |
|---|---|---|
| extra `query` (capability của ta) | `am start -a com.hermes.bridge.action.ASK -e query "Hermes, chay test du an"` | ✅ gửi `chay test du an` (đã cắt tiền tố "Hermes,") |
| `android.intent.extra.TEXT` | `-e android.intent.extra.TEXT "kiem tra dung luong dia"` | ✅ |
| deep link | `am start -a VIEW -d "hermes://ask?query=git%20status%20du%20an"` | ✅ |
| `VOICE_COMMAND` | `am start -a android.intent.action.VOICE_COMMAND -e query "…"` | ✅ |
| **app đang MỞ (hot start)** | (bug cũ nuốt mất) | ✅ **đã thêm lượt thứ 2 đúng** |
| "Hermes" trần, không có câu hỏi | `-e query "Hermes"` | ✅ **KHÔNG gửi** lượt vô nghĩa |

**VÒNG KHÉP KÍN CHẠY THẬT (đây là bằng chứng quan trọng nhất):**
```
am start -a com.hermes.bridge.action.ASK
  --es query "Hermes, cho toi biet 2+2 bang may"
→ app (banner "🎤 Từ Trợ lý Google", prompt "cho toi biet 2+2 bang may")
→ gateway Hermes THẬT (127.0.0.1:8642, hermes-agent 0.21.5)
→ Hermes trả lời: "2 + 2 = **4** bạn nhé!"
→ app đọc to ("🔊 Đang đọc trả lời…")
```
**Trợ lý Google → app → Hermes PC → trả lời → đọc to: XONG.**

- **Test:** Android **69/69 PASS** (51 cũ + **18 mới** `AssistantIntentTest`). npm **15/15 PASS**.
- **Build:** `assembleDebug` (12.59 MB) + `assembleRelease` (**4.01 MB**) OK. **Chữ ký release GIỮ NGUYÊN** `CN=Hermes Remote`, SHA-256 `f4fb5b41…7420` (apksigner verify: v2 OK) → **auto-update không gãy**. Đã cài **APK release** lên emulator và verify route Trợ lý **sống sót qua R8**.
- **Test bắt được 1 bug thật:** regex cắt tiền tố khớp `hermes` trước `hermes remote` → để lại rác `"Remote, chạy test"`. Đã sửa (khớp tên dài trước).
- **Vì sao emulator AOSP KHÔNG test được phần định tuyến của Trợ lý:** đã kiểm thật — `pm query-services -a android.service.voice.VoiceInteractionService` trả **"No services found"** (không có Google Play Services, không có `com.google.android.googlequicksearchbox`). Nên *hợp đồng intent* verify được 100%, còn *câu nói "Hey Google"* cần máy thật.

**CÒN LẠI (cần máy thật + tài khoản Google):**
1. Đăng ký App Action trên **Play Console** (hoặc Assistant test tool) để Trợ lý định tuyến "ask Hermes …" tới app.
2. Thử trên **điện thoại thật** có Trợ lý Google/Gemini.
3. (Nâng cấp tương lai) **AppFunctions** khi Google mở Early Access: cho Claude-Fable gọi thẳng hàm Kotlin trong app, không cần app mở.

### 0. TRỢ LÝ GIỌNG NÓI (A + B + C) — (branch `feat/voice-assistant`)
> Yêu cầu: biến app thành trợ lý ngoài đời thực, não vẫn trên Hermes PC, "Google Assistant thật" + wake word "Hey Hermes".
> **Sự thật quan trọng:** Google **đã khai tử Conversational Actions (2023)** → KHÔNG thể thay não "Hey Google" bằng app bên thứ ba. Vì vậy làm 3 tầng bổ trợ:
- **A. Trợ lý giọng trong app (lõi, khả thi 100%):** STT hệ thống (`SpeechRecognizerManager`) → `send()` → TTS hệ thống (`TtsSpeaker`, có strip markdown) đọc to. Nút 🎙 trong ô nhập (tap để nói). Wake word **"Hey Hermes"** qua **Porcupine** (`WakeWordDetector`) trong `VoiceAssistantService` (foreground, type `microphone`, notification "Hermes đang trợ lý"). **Thiếu AccessKey → fallback tap-to-talk, KHÔNG crash** (đã verify). `VoiceController` interface giữ ViewModel JVM-testable. `VoiceBus` nối service→UI. `BootReceiver` bật lại sau reboot.
- **B. App Actions (`res/xml/shortcuts.xml`):** action `com.hermes.bridge.action.ASK` + `OPEN_APP_FEATURE`; Assistant mở app với extra `query` → app tự gửi. **Đã verify intent:** `adb shell am start ... -a com.hermes.bridge.action.ASK --es query "..."` → app tự gửi + Hermes trả lời. Cần Play Services + Play Console để test đầy đủ (không test được hết trên ATD).
- **C. Webhook IFTTT/Google Home (packages/pairing-cli):** `POST /api/webhook/ask` (`lib/server.js`), xác thực token, forward sang gateway (non-stream), trả `{ok, reply}`; `?say=1` trả plain text cho loa đọc. Cờ CLI `--webhook-token <t>`. **Đã test thật với gateway Hermes:** `PONG` trả về đúng; sai token → 401.
- **Test:** Android **51/51 PASS** (37 cũ + 14 mới: `TtsSpeakerTest` 8, `VoiceStateTest` 6). npm **15/15 PASS** (7 cũ + 8 webhook). Build `assembleDebug` + `assembleRelease` OK; **release R8 giữ được Porcupine native libs (4 ABI) + chữ ký cũ nguyên** (SHA-256 `f4fb5b41…`); cài release lên emulator chạy OK, service voice OK, không crash.
- **Verify trên emulator được:** UI mic + settings toggle + service foreground + notification + App Action intent + chat regression. **KHÔNG verify được:** âm thanh thật (ATD không có mic/camera), STT thật (ATD không có recognition service), wake word thật (cần AccessKey + .ppn), App Actions đầy đủ (cần Play Services).
- **CÒN LẦI:** (1) đăng ký **Picovoice AccessKey** miễn phí + train file **`hey_hermes.ppn`** (đặt vào `apps/android/app/src/main/assets/`); (2) test giọng nói/wake word trên **máy Android thật**; (3) đăng ký App Actions + IFTTT applet (hướng dẫn ở `packages/pairing-cli/README.md`).

### 1. npm pack — `hermes-remote` (PC)
- **Đường dẫn:** `C:\Dev\Hermes_Android\packages/pairing-cli\`
- **Chạy:** `npx hermes-remote` (hoặc `npm install -g` từ tarball)
- **Việc nó làm:** đọc `.env` Hermes → ensure gateway chạy → mở **Cloudflare quick tunnel** (mặc định) → dựng webview trang QR → mở browser PC.
- **Payload QR:** `{v, baseUrl, fallbackUrl, apiKey, profile, model, profiles}` phục vụ tại `GET /api/connect`; QR PNG tại `/qr.png`; QR ASCII in ra terminal.
- **Đã test thật:** LAN mode OK; tunnel thật OK (public URL `/health` → 200, `/v1/models` → model thật); `npm pack` + `npm install -g --prefix <tmp>` + chạy binary OK. `node --test` → **5/5 PASS**.
- **Cờ:** `--tunnel` (mặc định) | `--no-tunnel`/`--lan` | `--port <n>` | `--no-open` | `-h`.
- **Đã verify lại (session 2):** `node bin/cli.js --lan --no-open --port 8899` → `/api/connect` 200 (payload đúng), `/` 200 (trang QR), `/qr.png` 200 (image/png ~6KB). Gateway Hermes thật đang chạy cổng **8642**.
- **CHÚ Ý:** `package.json` liệt `web/` trong `files` nhưng thư mục `web/` **RỖNG** (HTML render inline trong `lib/server.js`) → nên bỏ `"web/"` khỏi `files`.

### 2. Android app (điện thoại)
- **Đường dẫn:** `C:\Dev\Hermes_Android\apps/android\`
- **Tính năng:** app chat remote → gửi lệnh cho Hermes trên PC. Nút **"Quét QR"** → quét QR của npm pack → tự fill config (baseUrl/apiKey/profile/model) → tự kết nối.
- **APK debug:** `app\build\outputs\apk\debug\app-debug.apk` — **14.23 MB**, package `com.hermes.bridge.debug`.
- **APK release (ĐÃ KÝ):** `app\build\outputs\apk\release\app-release.apk` — **2.14 MB**, package `com.hermes.bridge` (không suffix), label "Hermes Remote". R8 minify + shrinkResources bật. Verify chữ ký OK: `CN=Hermes Remote`, SHA-256 `f4fb5b41…7420`.
- **Ký release:** đọc `apps/android/keystore.properties` (đã ignore trong git). Keystore ở **`C:\Users\GHC\hermes-keys\hermes-release.jks`** (alias `hermes`, mật khẩu ở `C:\Users\GHC\hermes-keys\keystore.pass` — cả 2 NGOÀI repo, KHÔNG commit). Nếu thiếu `keystore.properties`, release build tự fallback sang **unsigned** (không lỗi).
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
3. ~~Kiểm tra `/api/connect` khớp `ConnectPayload.kt`~~ → **ĐÃ XONG + CẢI THIỆN (session 3):** `/api/connect` khớp đúng `ConnectPayload.kt`. Phát hiện + sửa lỗ hổng thật: `profile` bị **hardcode `""`** ở cả `cli.js` lẫn `server.js` → QR không bao giờ chọn được profile dù máy có `dalek`, `doraemon`, `heimeringer`. **Thêm `--profile <tên>`:** validate theo `listProfiles()`, tên sai → fallback về key gốc kèm cảnh báo; tên đúng → luồn vào cả payload ASCII QR lẫn `/api/connect`. Verify thật: HTTP round-trip `profile="doraemon"` qua server sống OK; test 5→**7/7 PASS**.
4. ~~SỬA BUG LAYOUT nút "Git status" bị cắt cụt~~ → **ĐÃ SỬA (session 3):** thực ra row cũ đã có `horizontalScroll` (nút cuối cuộn tới được, chỉ là UI dump báo bounds ngoài màn hình — giống bug harness `jks:20`). Đổi sang **`FlowRow`** để nút tự xuống dòng, không nút nào bị khuất. Đã build lại OK.
5. ~~Bỏ `"web/"` khỏi `files`~~ → **ĐÃ XONG (session 3):** bỏ khỏi `package.json`, xóa luôn thư mục `packages/pairing-cli/web/` rỗng. `npm pack --dry-run` = 7 files, 9.3 kB.
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
- **Đổi tên file/folder theo quy tắc DẤU CHẤM** (1-2 từ cách nhau bằng `.`): **ĐÃ COMMIT** trong `02c4db7` — `CONTRIBUTING.md→docs.md`, `CHANGELOG.md→log.md`, `python-relay-server/→services/legacy-relay/` (kèm cập nhật path trong `docs.md`, `start_server.sh/.bat`).
- **Đã dọn:** xoá file rác `apps/android/real` (log lỡ ghi thành file) và `apps/android/NUL` (rác Windows).

## Trạng thái git (session 3)
- **ĐÃ CÓ REMOTE:** `origin` = `https://github.com/quangminh1212/hermes-remote.git` (PUBLIC) → push được.
- Commit mới nhất (mới nhất ở trên):
  - `feat(packages/pairing-cli): add --profile to pair the phone as a Hermes profile`
  - `feat(android): add self-update via GitHub Releases` (banner + tải + cài, 12 test mới)
  - `6c4fffc` feat(packages/pairing-cli): add hermes-remote pairing CLI
  - `f603926` feat(android): rewrite app as QR-paired HTTP/SSE chat client
  - `7d69579` chore(gitignore): stop tracking keystore.properties
  - `02c4db7` refactor: rename files/folders to dotted convention
- Working tree **SẠCH**. File ngoài git: `keystore.properties`, `C:\Users\GHC\hermes-keys\*` (KHÔNG bao giờ commit).
- **Phát hành bản mới cho auto-update:** bump `versionCode` trong `app/build.gradle.kts` → `assembleRelease` → `gh release create v<versionName>+<versionCode> app-release.apk`.
