# 第二道防线：荣耀家长控制 + 系统白名单

> 目标设备：荣耀平板 GDI-W09（Android 14 / MagicOS）
> 适用版本：儿童锁屏 1.0.1.14+

## 1. 为什么还要系统层

`dumpsys activity exit-info com.kidslock.app` 里 5 次强停全部来自同一个模块：

```
reason=10 (USER REQUESTED) subreason=21 (FORCE STOP)
description=stop com.kidslock.app due to from pid <system_server> iAwareF[SystemManager]
importance=125   # IMPORTANCE_FOREGROUND_SERVICE，有前台服务、无可见 Activity
```

最近一次（2026-11-10 14:04:25）尤其能说明问题：

- pss 只有 27MB，**不是内存压力**；
- 应用退到桌面 3 秒后被杀；
- 4 秒后同一个 `iAwareF` 把游戏 `com.je.skgame.honor` 也杀了；
- 属于一次批量清理（游戏助手退出清理 / 手机管家一键清理 / 系统自动清理）。

普通应用无法阻止 force-stop。force-stop 之后应用进入 `stopped=true`，静态广播、闹钟、JobScheduler 全部失效，只有手动点图标才能复活。

所以需要两层：

| 层 | 作用 | 是否依赖本应用进程 |
|---|---|---|
| 儿童锁屏 | 到点锁屏、答题解锁、计时 | 是 |
| 荣耀家长控制 | 应用限额、停用时段、总时长 | 否（系统应用） |

## 2. 第一层：系统白名单（必做，2 分钟）

### 2.1 应用启动管理

路径：手机管家 → 应用启动管理 → 儿童锁屏

- 关闭「自动管理」
- 打开三个开关：**自启动 / 关联启动 / 后台活动**

也可以用 adb 打开页面：

```powershell
adb shell am start -n com.hihonor.systemmanager/.startupmgr.ui.StartupNormalAppListActivity
```

### 2.2 耗电管理

路径：设置 → 应用 → 应用管理 → 儿童锁屏 → 耗电管理 → **不受限制**

### 2.3 最近任务加锁

最近任务里下拉儿童锁屏的卡片（或长按图标）→ 点锁图标。

### 2.4 游戏助手

路径：游戏助手 → 设置 → 关闭「退出游戏后清理后台」；
或把儿童锁屏加入游戏助手白名单。

### 2.5 关掉清理提醒

路径：手机管家 → 设置 → 关闭「清理提醒 / 内存加速提醒」。

> 通知栏里的「大量内存垃圾，点击释放 559MB」被点一下，就可能触发一次 force-stop。
> 这是目前最可疑的孩子可操作路径，建议直接关掉。

## 3. 第二层：荣耀家长控制（推荐）

设备上已内置系统应用 `com.hihonor.parentcontrol`（家长控制 / 儿童模式 / 健康使用平板）。

> 该应用的入口有系统签名权限保护，`adb shell am start` 会被拒绝：
> `SecurityException: requires hihonor.android.permission.HW_SIGNATURE_OR_SYSTEM`。
> 所以必须从系统 UI 进入。

### 3.1 入口（二选一）

- **A**：设置 → 搜索「儿童模式」或「健康使用平板」
- **B**：下拉通知栏 → 快捷开关里点「儿童模式」磁贴
  （该磁贴已通过 `adb shell cmd statusbar add-tile com.hihonor.parentcontrol/.service.ChildModeTileService` 添加；
  不想要可以执行 `adb shell cmd statusbar remove-tile com.hihonor.parentcontrol/.service.ChildModeTileService` 移除）

### 3.2 建议配置

1. **设置家长密码**（与儿童锁屏的 PIN 分开，避免孩子试出来）
2. **应用限额**：优酷、B 站、`com.je.skgame.honor`、`com.coowey.tv.com`（神马TV）设为每天 0 分钟或 30 分钟
3. **停用时段**：21:30 – 07:00
4. **可用总时长**：与儿童锁屏的观看时长保持一致（例如 60 分钟）
5. 关闭「允许安装未知来源应用」

### 3.3 注意

- 家长控制只做**外层兜底**，不要在里面再设「锁屏」，否则和儿童锁屏互相覆盖；
- 家长控制是系统应用，不受 iAware 启动管理约束，即使儿童锁屏被强停，应用限额仍然生效；
- 如果家长控制要求登录荣耀账号，用设备上已有的 `com.hihonor.id` 账号即可。

## 4. 验证

```powershell
# 1) 基线
adb shell dumpsys activity exit-info com.kidslock.app | findstr timestamp

# 2) 正常用 1-2 天，期间照常开游戏、视频
# 3) 复查
adb shell dumpsys activity exit-info com.kidslock.app | findstr timestamp
adb shell dumpsys package com.kidslock.app | findstr stopped
adb shell am get-standby-bucket com.kidslock.app   # 期望 5(EXEMPTED) 或 10(ACTIVE)
```

判断标准：

- **没有新增** `iAwareF[SystemManager]` → 白名单生效；
- **又新增** → 记录时间点，对照当时是否在用游戏/视频；
  连续两次以上再考虑「移除全部账号 → 设 Device Owner」（见 `docs/device-owner-kiosk.md`）。

## 5. 已知未解决问题

- 未设 Device Owner（设备有 4 个账号），因此：
  - 没有 LockTask，Home / 最近任务 / 通知栏仍可切走（靠 overlay + watchdog 在 1–5 秒内抓回）；
  - 没有防卸载；
  - 强停后无法自动复活，只能等下次有人点开应用，届时 `deductOfflineIfAny()` 会把离线时间一次性扣掉。
- `com.coowey.tv.com`（神马TV）是 2026-11-07 22:48 通过第三方安装器 `app.yecao` 安装的，不是应用市场来源。
