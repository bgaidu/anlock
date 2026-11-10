# 设备所有者（Device Owner）+ LockTask 部署说明

> 对应版本：1.0.1.14（versionCode 17）
> 目标设备：荣耀平板 GDI-W09（Android 14 / MagicOS）

## 1. 为什么需要这一层

`dumpsys activity exit-info com.kidslock.app` 里 4 次强停全部是同一个来源：

```
reason=10 (USER REQUESTED) subreason=21 (FORCE STOP)
description=stop com.kidslock.app due to from pid 1672 iAwareF[SystemManager]
importance=125   # IMPORTANCE_FOREGROUND_SERVICE：有前台服务、无可见 Activity
```

- `pid 1672` 是 `system_server`，即荣耀系统级后台/内存/启动管理；
- 4 次都发生在“切到游戏 / B 站，儿童锁屏退到后台”的瞬间；
- force-stop 之后应用进入 `stopped=true iAwareStopped=true`，静态广播、闹钟、JobScheduler 全部失效，只能手动点开图标复活。

普通应用权限挡不住 force-stop。只有 **Device Owner** 才能：

- 用 `LockTask` 把锁屏变成 kiosk：Home、最近任务、通知栏、电源菜单全部锁死；
- 让设置里的“强行停止/卸载”按钮失效（`setUninstallBlocked`）；
- 在锁定期间禁用系统 keyguard，避免系统密码盖在儿童锁屏之上；
- 通过 `DeviceAdminService` 在开机时被系统主动绑定，多一条复活通道。

## 2. 代码侧已经做了什么

| 文件 | 变更 |
|---|---|
| `KidsDeviceAdminReceiver.kt` | 新增设备管理器入口，`dpm set-device-owner` 的目标组件 |
| `KidsDeviceAdminService.kt` | 新增；Device Owner 开机绑定，补扣离线时间并拉起 `LockService` |
| `KioskManager.kt` | 新增；封装 DO 检测、`setLockTaskPackages`、`setUninstallBlocked`、`setLockTaskFeatures(NONE)`、`setKeyguardDisabled`、`startLockTask/stopLockTask` |
| `LockScreenActivity.kt` | `onResume` 且在锁定状态时进入 LockTask；`onPause` 立刻用 overlay 补位；解锁时退出 LockTask |
| `LockService.kt` | 拉起锁屏前不再先撤 overlay（消除裸奔窗口）；服务启动/触发锁定/解锁时同步 DO 策略；启动时 `deductOfflineIfAny()` |
| `LockGuard.kt` | 熄屏时不再反复拉起；overlay 已覆盖时降频到 5s，未覆盖时 1s；两次拉起最小间隔 1.5s |
| `BootReceiver.kt` | 开机时同步 DO 策略 |
| `MainActivity.kt` | 进程重建时补扣离线时间；状态栏显示“设备所有者” |
| `AndroidManifest.xml` | 注册 device admin receiver + `DeviceAdminService` |

未成为 Device Owner 时，以上逻辑全部安静跳过，应用保持原行为。

## 3. 启用步骤

### 3.1 前置条件（缺一不可）

1. 应用已安装；
2. 设备上**没有任何账号**（设置 → 用户和账户；Honor 账号、Google 账号、Exchange 等都要移除）；
3. 设备上**只有一个用户**（没有访客/隐私空间/工作资料）。

> 移除 Honor 账号会同时关闭“查找我的设备”，请在操作前确认可以接受。

检查命令：

```powershell
adb shell dumpsys account | findstr /i "Account {"
adb shell pm list users
```

### 3.2 设置为 Device Owner

```powershell
adb shell dpm set-device-owner com.kidslock.app/.KidsDeviceAdminReceiver
adb shell dpm list-owners
```

预期输出包含：

```
User 0: admin=ComponentInfo{com.kidslock.app/com.kidslock.app.KidsDeviceAdminReceiver}
```

### 3.3 验证

```powershell
# 设备所有者是否生效
adb shell dumpsys device_policy | findstr /i "Device Owner"
# 锁屏时应处于 lock task 模式
adb shell dumpsys activity | findstr /i "mLockTaskModeState"
# 卸载保护
adb shell dumpsys package com.kidslock.app | findstr /i "uninstall"
```

应用内“守护服务”按钮会显示 `· 设备所有者`。

### 3.4 撤销（仅家长使用）

```powershell
adb shell dpm remove-active-admin com.kidslock.app/.KidsDeviceAdminReceiver
```

> 成为 DO 后无法在设置里普通卸载；必须先执行上面的命令，或在恢复出厂设置后重来。

## 4. 如果 DO 设置失败

常见原因与处理：

| 报错 | 处理 |
|---|---|
| `already some accounts on the device` | 移除全部系统账号后重试 |
| `already several users` | 删除访客/隐私空间/工作资料 |
| `Unknown admin` | 包名或 Receiver 类名不对，确认 APK 已安装 |
| `device owner is already set` | 设备上已有其他 DO，先 `dpm list-owners` 查清 |

DO 不可用时，退回到设备侧白名单（仍需家长手动操作）：

0. 至少激活设备管理器（不需要移除账号），让 `DeviceAdminService` 多一条开机绑定通道：
   ```powershell
   adb shell dpm set-active-admin com.kidslock.app/.KidsDeviceAdminReceiver
   ```
1. 手机管家 → 应用启动管理 → 儿童锁屏 → **手动管理**，自启动 / 关联启动 / 后台活动三个开关全开；
2. 设置 → 应用 → 儿童锁屏 → 耗电管理 → **不受限制**；
3. 最近任务里给儿童锁屏**加锁**；
4. 游戏助手 → 关闭自动清理，或把儿童锁屏加入白名单；
5. 关闭“纯净模式/应用管控”（如有）；
6. 特殊访问权限：显示在其他应用上层、后台弹出界面、锁屏显示、使用情况访问全部允许。

## 5. 签名与升级注意

- 设备上已安装的 `1.0.1.13` 是 **CI release 签名**，本地仓库没有对应 keystore（`.gitignore` 已排除 `*.jks/*.keystore`，密钥只在 GitHub Secrets）。
- 本地 `assembleDebug` 产物是 debug 签名，**不能覆盖安装**到已装的 release 版本。
- 两种升级方式：
  1. **推荐**：推送 `v1.0.1.14` 标签，让 GitHub Actions 用同一把 release key 构建并发布，下载后 `adb install -r`，保留 PIN 与设置；
  2. 卸载后安装 debug 版：会丢失 PIN、观看时长、计时状态和答题统计，需要重新配置。
- 版本号已提升到 `1.0.1.14 / versionCode 17`，可用 `dumpsys package com.kidslock.app | findstr versionName` 确认装的是新版。

## 6. 改完后的验证步骤

```powershell
# 1) 记录基线
adb shell dumpsys activity exit-info com.kidslock.app | findstr timestamp

# 2) 正常使用 30 分钟，期间启动 B 站/游戏
# 3) 再查一次，确认没有新增 iAwareF[SystemManager] 记录
adb shell dumpsys activity exit-info com.kidslock.app | findstr timestamp
adb shell dumpsys package com.kidslock.app | findstr stopped

# 4) 若仍被强停，抓系统事件
adb logcat -b events -v time | findstr /i "kidslock am_kill am_proc_died"
```

同时可在平板“手机管家 → 应用行为记录 / 清理记录”里核对强停时刻是否有手工清理动作，以区分“系统自动清理”和“孩子手动清理”。

## 7. 忘记 PIN / 被锁死的应急处理

按风险从低到高：

1. **紧急解锁文件**（应用原有功能）：
   ```
   adb shell "echo unlock > /sdcard/Android/data/com.kidslock.app/files/kidslock_emergency_unlock"
   ```
   然后重启设备，`BootReceiver` 检测到文件会跳过锁屏。
2. **临时退出 kiosk**：
   ```powershell
   adb shell am force-stop com.kidslock.app
   ```
   锁屏进程被停掉后 LockTask 会结束；随后可正常操作设置。
3. **彻底移除 Device Owner**：
   ```powershell
   adb shell dpm remove-active-admin com.kidslock.app/.KidsDeviceAdminReceiver
   adb uninstall com.kidslock.app
   ```
4. 以上都不可用时，只能恢复出厂设置（会清空全部数据）。

> 成为 Device Owner 后，设置里的“强行停止 / 卸载 / 清除数据”对儿童锁屏会变灰，这是预期行为。

