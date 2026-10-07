<#
.SYNOPSIS
    Cai Hermes Remote len dien thoai Android dang cam USB va kiem tra ket noi.

.DESCRIPTION
    Script nay lam het cac buoc con lai sau khi ban cam dien thoai:
      1. Kiem tra adb + thiet bi
      2. Cai APK debug (hoac release) len dien thoai
      3. Kiem tra gateway Hermes tren PC dang chay
      4. Khoi dong pairing-cli de pair

.PARAMETER Mode
    debug  - cai app-debug.apk (khuyen dung khi dang phat trien)
    release- cai app-release.apk (nhe hon, nhanh hon)
    pair   - chi khoi dong pairing-cli, khong cai APK
    check  - chi kiem tra moi truong, khong lam gi ca

.PARAMETER Lan
    Dung --lan: di qua WiFi noi bo thay vi Cloudflare tunnel.

.EXAMPLE
    .\scripts\test-on-phone.ps1 -Mode debug
    .\scripts\test-on-phone.ps1 -Mode pair -Lan
#>
[CmdletBinding()]
param(
    [ValidateSet('debug', 'release', 'pair', 'check')]
    [string]$Mode = 'check',

    [switch]$Lan
)

$ErrorActionPreference = 'Stop'

# --- Duong dan ---------------------------------------------------------------
$Repo     = Split-Path -Parent $PSScriptRoot
$Adb      = 'C:\Users\GHC\AndroidSdk\platform-tools\adb.exe'
$ApkDebug = Join-Path $Repo 'apps\android\app\build\outputs\apk\debug\app-debug.apk'
$ApkRel   = Join-Path $Repo 'apps\android\app\build\outputs\apk\release\app-release.apk'
$Pairing  = Join-Path $Repo 'packages\pairing-cli'
$GatewayPort = 8642

function Say  ($m) { Write-Host $m }
function Ok   ($m) { Write-Host "  [OK]   $m"   -ForegroundColor Green }
function Warn ($m) { Write-Host "  [CANH] $m"   -ForegroundColor Yellow }
function Bad  ($m) { Write-Host "  [LOI]  $m"   -ForegroundColor Red }
function Head ($m) { Write-Host ""; Write-Host "== $m ==" -ForegroundColor Cyan }

# --- 1. adb ------------------------------------------------------------------
Head 'Buoc 1: adb va thiet bi'

if (-not (Test-Path $Adb)) {
    Bad "Khong tim thay adb tai $Adb"
    Say '     Sua: kiem tra Android SDK, hoac cai platform-tools.'
    exit 1
}
Ok "adb: $((& $Adb version | Select-Object -First 1))"

$devices = & $Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\S' }

$real = $devices | Where-Object { $_ -match '\sdevice$' }
$bad  = $devices | Where-Object { $_ -match '\s(unauthorized|offline)$' }

if ($bad) {
    $st = ($bad | Select-Object -First 1) -replace '.*\s', ''
    Bad "Thiet bi o trang thai '$st'."
    if ($st -eq 'unauthorized') {
        Say '     -> Tren dien thoai, bam "Cho phep go loi USB" cho may tinh nay.'
        Say '     -> Neu khong thay hop thoai: Tat/Cam lai cap USB, hoac'
        Say '        Tat go loi USB roi bat lai trong Tuy chon nha phat trien.'
    } else {
        Say '     -> Thu cam lai cap USB hoac chay: adb kill-server; adb start-server'
    }
    exit 1
}

if (-not $real) {
    Warn 'Chua co dien thoai nao ket noi.'
    Say ''
    Say '  LAM THEO CAC BUOC SAU TREN DIEN THOAI:'
    Say '    1. Cai dat -> Gioi thieu dien thoai -> Nhan 7 lan vao "So ban dung"'
    Say '       (hoac "Build number") de mo Tuy chon nha phat trien.'
    Say '    2. Tuy chon nha phat trien -> bat "Go loi USB" (USB debugging).'
    Say '    3. Cam cap USB vao may tinh.'
    Say '    4. Tren dien thoai hien hop thoai -> bam "Cho phep".'
    Say '    5. Chay lai script nay.'
    Say ''
    Say '  HOAC khong can day - tai APK truc tiep:'
    if (Test-Path $ApkRel) { Say "    $ApkRel" }
    Say '    Chep file .apk sang dien thoai, mo bang trinh quan ly file de cai.'
    Say '    (Can bat "Cai dat ung dung khong ro nguon goc".)'
    exit 0
}

$serial = ($real | Select-Object -First 1) -split '\s+' | Select-Object -First 1
Ok "Thiet bi: $serial"
Write-Host ""
& $Adb devices -l

$model = (& $Adb -s $serial shell getprop ro.product.model) -replace '\s', ''
$sdkv  = (& $Adb -s $serial shell getprop ro.build.version.sdk) -replace '\s', ''
Ok "Model: $model  |  API level: $sdkv"

if ([int]$sdkv -lt 26) {
    Bad "Can Android 8.0 (API 26) tro len. Thiet bi nay la API $sdkv."
    exit 1
}

if ($Mode -eq 'check') {
    Head 'Xong (che do check)'
    Ok 'Moi truong san sang. Chay lai voi -Mode debug de cai APK.'
    exit 0
}

# --- 2. Cai APK --------------------------------------------------------------
if ($Mode -in @('debug', 'release')) {
    Head 'Buoc 2: Cai APK'

    $apk = if ($Mode -eq 'debug') { $ApkDebug } else { $ApkRel }

    if (-not (Test-Path $apk)) {
        Bad "Khong co APK: $apk"
        Say '     Build no truoc:'
        Say "       cd `"$Repo\apps\android`""
        Say '       .\gradlew.bat assembleDebug'
        exit 1
    }

    $mb = [math]::Round((Get-Item $apk).Length / 1MB, 2)
    Ok "APK ($Mode): $apk  ($mb MB)"

    Say '  Dang cai...'
    $install = & $Adb -s $serial install -r $apk 2>&1
    $install | ForEach-Object { Write-Host "    $_" }

    if ($install -match 'Success') {
        Ok 'Cai dat thanh cong.'
    } else {
        Bad 'Cai dat that bai. Xem thong bao ben tren.'
        Say '     Loi thuong gap:'
        Say '       INSTALL_FAILED_UPDATE_INCOMPATIBLE -> go app cu truoc:'
        Say '         adb uninstall io.hermes.remote   (hoac io.hermes.remote.debug)'
        Say '       INSTALL_FAILED_VERSION_DOWNGRADE  -> them -d:'
        Say '         adb install -r -d <apk>'
        exit 1
    }
}

# --- 2b. Kiem tra duong LAN (khi dung -Lan) ----------------------------------
if ($Lan) {
    Head 'Buoc 2b: Kiem tra duong LAN (WiFi cua dien thoai -> PC)'

    $lanIp   = (& node -e "const os=require('os');const n=os.networkInterfaces();for(const k of Object.keys(n)){for(const i of n[k]||[]){if(i.family==='IPv4'&&!i.internal){console.log(i.address);process.exit(0)}}console.log('127.0.0.1')").Trim()
    Ok "IP LAN ma CLI se dung: $lanIp"

    $wifi = netsh wlan show interfaces 2>&1
    if ($wifi -match 'no wireless interface') {
        Warn 'PC khong co card WiFi. Diem nay KHONG sao:'
        Say  '     Mien la dien thoai vao WiFi cua CUNG router voi PC thi thong nhau.'
        Say  "     Vi du: dien thoai 192.168.1.x  ->  PC $lanIp"
    }

    $listen = Get-NetTCPConnection -LocalPort $GatewayPort -State Listen -ErrorAction SilentlyContinue
    if (-not $listen) {
        Warn "Gateway chua listen tren port $GatewayPort - se duoc khoi dong boi pairing-cli."
    }
    $bound = $listen | Where-Object { $_.LocalAddress -notin @('0.0.0.0', '::') }
    if ($bound) {
        Warn "Gateway chi bind vao $($bound.LocalAddress -join ', ') -> dien thoai se KHONG vao duoc!"
        Say  '     Can bind 0.0.0.0. Sua trong cau hinh gateway Hermes.'
    } elseif ($listen) {
        Ok "Gateway listen tren 0.0.0.0:$GatewayPort (moi card deu vao duoc)."
    }

    Say ''
    Say '  >>> TU KIEM TRA TREN DIEN THOAI (quan trong nhat):'
    Say  "      Mo trinh duyet dien thoai, vao:  http://${lanIp}:$GatewayPort/health"
    Say  '      - Hien JSON  -> LAN thong, pair se chay.'
    Say  '      - Xoay/loi   -> dien thoai KHAC mang, hoac firewall/router chan.'
    Say  '      Kiem tra WiFi dien thoai dang o cung router voi PC (vd 192.168.1.x).'
    Say ''
}

if ($Mode -eq 'check') { exit 0 }

# --- 3. Gateway --------------------------------------------------------------
Head 'Buoc 3: Gateway Hermes tren PC'

$listen = Get-NetTCPConnection -LocalPort $GatewayPort -State Listen -ErrorAction SilentlyContinue
if ($listen) {
    Ok "Gateway dang chay tren port $GatewayPort (PID $($listen.OwningProcess))"
} else {
    Warn "Khong thay gi listen tren port $GatewayPort."
    Say '     pairing-cli se tu khoi dong gateway neu can.'
}

# --- 4. Pairing ---------------------------------------------------------------
Head 'Buoc 4: Pairing'

if (-not (Test-Path (Join-Path $Pairing 'node_modules'))) {
    Warn 'Chua co node_modules trong pairing-cli -> chay npm install...'
    Push-Location $Pairing
    npm install
    Pop-Location
}

$args = @()
if ($Lan) { $args += '--lan' }

Say ''
Say '  Sap chay: npx hermes-remote ' + ($args -join ' ')
Say '  -> Trinh duyet se mo trang QR.'
Say '  -> Tren dien thoai: mo Hermes Remote, bam "Quet QR", quet trang do.'
Say ''
if ($Lan) {
    Warn 'Che do --lan: dien thoai PHAI cung WiFi/router voi PC.'
    Say  '     Neu khong ket noi duoc, kiem tra Windows Firewall va AP isolation.'
} else {
    Say '  Che do tunnel: khong can cung WiFi. Hoat dong ca tren 4G.'
}
Say ''
Say '  Nhan Ctrl+C de dung.'
Say ''

Push-Location $Pairing
try {
    if ($args.Count) { npx hermes-remote @args } else { npx hermes-remote }
} finally {
    Pop-Location
}
