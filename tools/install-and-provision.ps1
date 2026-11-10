param(
    [string]$Serial = "",
    [string]$Apk = "",
    [switch]$Replace,
    [switch]$SetDeviceOwner
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$adb = Join-Path $repo 'android-sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "adb not found: $adb" }
if ([string]::IsNullOrEmpty($Apk)) {
    $Apk = Join-Path $repo 'app\build\outputs\apk\release\app-universal-release.apk'
}
if (-not (Test-Path $Apk)) { throw "APK not found: $Apk" }

if ([string]::IsNullOrEmpty($Serial)) {
    $lines = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\s+device\s*$' })
    if ($lines.Count -eq 0) { throw "No authorized device. Connect USB and allow USB debugging." }
    if ($lines.Count -gt 1) { throw "Multiple devices found. Use -Serial." }
    $Serial = ($lines[0] -split '\s+')[0]
}

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$CmdArgs)
    & $adb -s $Serial @CmdArgs
}

Write-Host "== Device $Serial =="
Adb shell getprop ro.product.model
Adb shell getprop ro.build.version.release
Write-Host ""

Write-Host "== Install =="
if ($Replace) {
    Adb uninstall com.kidslock.app | Out-Null
    Adb install $Apk
} else {
    Adb install -r $Apk
}
Write-Host ""

Write-Host "== Permissions =="
Adb shell pm grant com.kidslock.app android.permission.WRITE_SECURE_SETTINGS
Adb shell pm grant com.kidslock.app android.permission.POST_NOTIFICATIONS
Adb shell appops set com.kidslock.app SYSTEM_ALERT_WINDOW allow
Adb shell appops set com.kidslock.app GET_USAGE_STATS allow
Adb shell appops set com.kidslock.app SCHEDULE_EXACT_ALARM allow
Adb shell cmd deviceidle whitelist +com.kidslock.app
Adb shell settings put secure enabled_accessibility_services com.kidslock.app/com.kidslock.app.RecoveryAccessibilityService
Adb shell settings put secure accessibility_enabled 1
Write-Host ""

Write-Host "== Device Owner feasibility =="
Adb shell pm list users
Adb shell dumpsys account | Select-String -Pattern 'Account \{' | ForEach-Object { $_.Line.Trim() }
Adb shell dpm list-owners

if ($SetDeviceOwner) {
    $accounts = @(Adb shell dumpsys account | Select-String -Pattern 'Account \{')
    $users = @(Adb shell pm list users | Select-String -Pattern 'UserInfo\{')
    if ($accounts.Count -gt 0) {
        Write-Warning "Device still has $($accounts.Count) account(s). Remove accounts before dpm set-device-owner."
    } elseif ($users.Count -gt 1) {
        Write-Warning "Device has multiple users. Remove guests/work profile first."
    } else {
        Adb shell dpm set-device-owner com.kidslock.app/.KidsDeviceAdminReceiver
        Adb shell dpm list-owners
    }
}
Write-Host ""

Write-Host "== Launch =="
Adb shell "monkey -p com.kidslock.app -c android.intent.category.LAUNCHER 1" | Out-Null
Start-Sleep -Seconds 2
Adb shell dumpsys package com.kidslock.app | Select-String -Pattern 'versionName|stopped='
Adb shell dumpsys activity | Select-String -Pattern 'mLockTaskModeState'
