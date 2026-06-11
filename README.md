# FMS Android App

FMS 文件管理系统 Android 客户端，基于 [Capacitor](https://capacitorjs.com/) WebView 封装。

## 功能

- 🌐 WebView 加载服务端页面（FMS-Service）
- 🔍 扫一扫登录（QR Code 扫码）
- ⚙️ 自定义服务器地址（SharedPreferences 持久化）
- 🚀 首次启动自动读取 `assets/server_config.json` 默认地址
- ℹ️ 关于页面（版本信息 + GitHub 跳转）
- 📤 文件分享接收（UploadActivity）
- 🎨 启动画面（Splash Screen）
- 🔄 应用更新检测

## 构建

```bash
# 1. 写入版本信息到 assets
node ../scripts/pre-build.js "更新日志内容"

# 2. 构建 APK
./gradlew assembleRelease

# APK 输出路径
# app/build/outputs/apk/release/app-release.apk
```

## 版本号

| 字段 | 位置 | 示例 |
|------|------|------|
| versionCode | `app/build.gradle:versionCode` | 20400 |
| versionName | `app/build.gradle:versionName` | "2.4.0" |

## 关联项目

| 项目 | 仓库 |
|------|------|
| 🌐 **服务端** | [Antruly/FMS-Service](https://github.com/Antruly/FMS-Service) |
| 📱 **Android App** | [Antruly/FMS-Service-app](https://github.com/Antruly/FMS-Service-app) |

## 开源协议

[MIT License](LICENSE)
