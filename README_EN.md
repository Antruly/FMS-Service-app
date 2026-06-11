# FMS Android App

Android client for FMS File Management System, built with [Capacitor](https://capacitorjs.com/) WebView.

## Features

- 🌐 WebView-based UI (loads from FMS-Service server)
- 🔍 QR Code login (scan to authenticate)
- ⚙️ Custom server URL (persisted via SharedPreferences)
- 🚀 Auto-loads default server from `assets/server_config.json` on first launch
- ℹ️ About page (version info + GitHub links)
- 📤 Share-to-Upload receiver (UploadActivity)
- 🎨 Splash Screen
- 🔄 In-app update detection

## Build

```bash
# 1. Write version info to assets
node ../scripts/pre-build.js "Changelog entry"

# 2. Build APK
./gradlew assembleRelease

# APK output
# app/build/outputs/apk/release/app-release.apk
```

## Versioning

| Field | Location | Example |
|-------|----------|---------|
| versionCode | `app/build.gradle:versionCode` | 20400 |
| versionName | `app/build.gradle:versionName` | "2.4.0" |

## Related Projects

| Project | Repository |
|---------|------------|
| 🌐 **Server** | [Antruly/FMS-Service](https://github.com/Antruly/FMS-Service) |
| 📱 **Android App** | [Antruly/FMS-Service-app](https://github.com/Antruly/FMS-Service-app) |

## License

[MIT License](LICENSE)
