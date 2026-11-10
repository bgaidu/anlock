param(
    [string]$Serial = "",
    [int]$WaitMinutes = 20,
    [string]$LogFile = "D:\Personal\Temp\opencode\lock-drill.log"
)

# Automated escape drill: waits for the kids lock screen to engage, then tries
# HOME / recents / notification shade / launching a game / launching a video app,
# and records whether the lock screen wins back the foreground each time.
# ASCII-only on purpose: PS 5.1 misparses UTF-8 Chinese in .ps1 files.

$ErrorActionPreference = 'Continue'
$repo = Split-Path $PSScriptRoot -Parent
$adb = Join-Path $repo 'android-sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "adb not found: $adb" }

if ([string]::IsNullOrEmpty($Serial)) {
    $lines = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\s+device\s*$' })
    if ($lines.Count -eq 0) { throw "No authorized device." }
    $Serial = ($lines[0] -split '\s+')[0]
}

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$CmdArgs)
    & $adb -s $Serial @CmdArgs
}

function Log {
    param([string]$m)
    $line = "[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m
    Add-Content -Path $LogFile -Value $line
    Write-Host $line
}

function FocusNow {
    $o = Adb shell "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'"
    return (($o | ForEach-Object { $_.Trim() }) -join ' || ')
}

function Sample {
    param([string]$tag, [int]$sec)
    Start-Sleep -Seconds $sec
    $f = FocusNow
    $verdict = if ($f -match 'com\.kidslock\.app') { 'LOCKED' } else { 'ESCAPED' }
    Log ("{0} +{1}s => {2} : {3}" -f $tag, $sec, $verdict, $f)
}

Set-Content -Path $LogFile -Value ("== lock drill start {0} ==" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))

Adb shell svc power stayon true | Out-Null
Adb shell input keyevent 3 | Out-Null
Start-Sleep -Seconds 2

$f = FocusNow
if ($f -match 'com\.kidslock\.app') {
    Log "WARN: kidslock already focused before the lock engaged: $f"
}

Log ("Waiting up to {0} min for lock (screen stays on)..." -f $WaitMinutes)
$deadline = (Get-Date).AddMinutes($WaitMinutes)
$locked = $false
while ((Get-Date) -lt $deadline) {
    $f = FocusNow
    if ($f -match 'com\.kidslock\.app') { $locked = $true; break }
    Start-Sleep -Seconds 5
}

if (-not $locked) {
    Log "TIMEOUT: lock never engaged. last focus: $f"
    Adb shell svc power stayon false | Out-Null
    exit 1
}

Log "Lock engaged: $f"
Sample 'baseline' 3

Log '-- drill 1: HOME --'
Adb shell input keyevent 3 | Out-Null
Sample 'HOME' 1
Sample 'HOME' 5

Log '-- drill 2: recents --'
Adb shell input keyevent 187 | Out-Null
Sample 'RECENTS' 1
Sample 'RECENTS' 5

Log '-- drill 3: notification shade --'
Adb shell cmd statusbar expand-notifications | Out-Null
Sample 'SHADE' 1
Sample 'SHADE' 5
Adb shell cmd statusbar collapse | Out-Null
Sample 'SHADE-COLLAPSE' 2

Log '-- drill 4: launch game --'
Adb shell am start -n com.je.skgame.honor/com.je.game.JeMainActivity | Out-Null
Sample 'GAME' 2
Sample 'GAME' 6

Log '-- drill 5: launch video app --'
Adb shell am start -n com.hihonor.youku.video/com.youku.v2.HomePageEntry | Out-Null
Sample 'YOUKU' 2
Sample 'YOUKU' 6

Log '-- drill 6: final verdict --'
Log ("focus: " + (FocusNow))
$ei = Adb shell "dumpsys activity exit-info com.kidslock.app | grep -E 'timestamp=|description='"
$ei | Select-Object -First 6 | ForEach-Object { Log ("exit-info: " + $_.Trim()) }
$pkg = Adb shell "dumpsys package com.kidslock.app | grep -E 'stopped=|versionName'"
$pkg | ForEach-Object { Log ("pkg: " + $_.Trim()) }

Adb shell svc power stayon false | Out-Null
Log "== drill done =="
