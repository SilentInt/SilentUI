# SilentUI

把 `SuGesture`（root 手势导航）与 `customstatusbar`（悬浮自定义状态栏 / 快捷面板）融合为一个应用。

- 包名：`com.silentui`
- 应用名：`SilentUI`
- minSdk 24 / targetSdk 36
- 无 AndroidX，纯 Java + 程序化 UI，可用 Android SDK 命令行直接构建

## 功能

- 手势导航：左/右边缘内滑、底部上滑、底部上滑并按住；动作可配置为按键、启动应用、自定义按键码或 root shell 命令。
- 状态栏：顶部悬浮状态栏（左侧自定义名称/通知数、中央时钟、右侧 WiFi/蓝牙/电量）。
- 状态栏显示模式：
  - 固定位置：常驻顶部并设置 WM 留白（`wm overscan`）。
  - 触摸显示：保持原大小，触摸/下拉时显示，超时自动变透明（时长可调，默认 2 秒），保持 WM 全屏。
- 快捷面板：点击状态栏展开；包含 WiFi、蓝牙、设置、自定义应用入口、背光调节与通知列表（触摸显示模式下整体下移避开状态栏）。
- 状态栏手势：左/右下滑执行指定动作，横滑调背光，上滑收起。
- 开机自启：通过 Magisk `service.d` 脚本（`/data/adb/service.d/SilentUI.sh`）拉起手势守护进程；守护进程内的看门狗线程会不断「授权并启动」状态栏服务，未就绪时自动重试。
- 状态栏服务不通过前台服务通知保活。
- 手势按键动作优先走 binder 快速注入，注入失败才回退 `input keyevent`。
- 启动流程：MainActivity 启动时先检查权限 → 非阻塞自动授权 → 启动手势导航与状态栏；权限完整则直接启动，不完整且可 root 时自动重启以完成授权。
- 启动状态栏服务时自动执行 `settings put global policy_control immersive.full=*` 隐藏系统默认状态栏；停止时恢复。

## 目录结构

```
SilentUI/
  app/src/main/AndroidManifest.xml
  app/src/main/java/com/silentui/*.java
  app/src/main/res/
  tools/merge_sources.ps1   # 从两个原始项目生成 com/silentui 源码（一次性）
  build.ps1                 # 用 android-sdk 构建 SilentUI.apk / SilentUI-release.apk
  debug.keystore            # 测试签名（androiddebugkey / android）
  settings.gradle.kts 等    # Android Studio / Gradle 工程文件
```

## 构建 APK

要求：已安装 JDK（`JAVA_HOME` 已设置）和 Android SDK build-tools 36.0.0。

```powershell
cd SilentUI
pwsh -NoProfile -ExecutionPolicy Bypass -File .\build.ps1                 # 产出 SilentUI.apk
pwsh -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -Release        # 产出 SilentUI-release.apk
```

产物在 `SilentUI\` 目录下，使用仓库内的 `debug.keystore` 测试签名（别名 `androiddebugkey`，密码 `android`）。

`tools\merge_sources.ps1` 仅用于首次从两个原始项目生成 `com.silentui` 源码；当前仓库已包含生成并修复后的源码，正常构建无需再次运行它。

也可以用 Android Studio 打开本目录后通过 Gradle 构建（AGP 9.0.0）。

## 安装与使用

```powershell
adb install -r SilentUI.apk
```

首次使用：
1. 打开 SilentUI（自动检查权限并授权，随后启动手势导航与状态栏；若权限不完整会自动重启一次）。
2. 进入「手势导航」页确认手势已启用。
3. 进入「状态栏」页选择显示模式（固定位置 / 触摸显示）并调节自动隐藏时长。

## 说明

- 手势守护进程以 root 身份通过 `app_process` 加载 APK 内的 `com.silentui.GestureDaemon`，退出本应用后仍独立运行；配置与日志在 `/data/local/tmp/SilentUI.conf`、`/data/local/tmp/SilentUId.log`。
- 状态栏为 overlay 窗口，依赖 `SYSTEM_ALERT_WINDOW`；背光调节依赖 `WRITE_SETTINGS`。
