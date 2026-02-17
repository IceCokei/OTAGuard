# 🛡️ OTA Guard

A dual-layer Android application + LSPosed module that monitors and blocks system OTA updates on OnePlus/ColorOS devices.

## ❓ Why

ColorOS can silently re-enable OTA services, reset update settings, or push hidden updates in the background — even after you've manually disabled them. OTA Guard prevents this at both the application and system level.

## ⚙️ How It Works

### 📱 App Layer
- Uses root privileges to check the enabled/disabled state of 5 OTA-related system packages
- Reads 3 critical `Settings.Global` values that control automatic update behavior
- Provides a one-tap "Enforce" button to re-freeze everything if tampering is detected

### 🪝 LSPosed Hook Layer
- **Settings Protection** — Hooks `Settings.Global.putString` and `putInt` to block any attempt to change OTA-related settings back to their defaults
- **Package Protection** — Hooks `PackageManagerService.setApplicationEnabledSetting` to prevent OTA packages from being re-enabled
- **Process Kill** — Hooks `Application.onCreate` inside OTA packages to immediately terminate them if they somehow launch
- **Network Block** — Hooks `URL.openConnection` inside OTA packages to block update check requests

## 🎯 Monitored Targets

| Type | Target | Expected |
|------|--------|----------|
| 📦 Package | `com.oplus.ota` | 🚫 disabled |
| 📦 Package | `com.oplus.cota` | 🚫 disabled |
| 📦 Package | `com.oplus.romupdate` | 🚫 disabled |
| 📦 Package | `com.oplus.upgradeguide` | 🚫 disabled |
| 📦 Package | `com.google.android.configupdater` | 🚫 disabled |
| ⚙️ Setting | `ota_disable_automatic_update` | ✅ 1 |
| ⚙️ Setting | `auto_download_network_type` | ✅ 0 |
| ⚙️ Setting | `can_update_at_night` | ✅ 0 |

## 📋 Requirements

- Android 11+ (API 30)
- Root access (Magisk / KernelSU)
- LSPosed framework

## 🧪 Tested Environment

- **Device**: OnePlus 15
- **System**: ColorOS 16 (Android 16)
- Other OEM / system versions are untested — use at your own risk

## 📥 Install

1. Download the latest release APK from [Releases](https://github.com/IceCokei/OTAGuard/releases)
2. Install the APK
3. Open LSPosed Manager → Modules → Enable **OTA Guard**
4. Select scope: `System Framework (android)` + all OTA packages + `OTA Guard` itself
5. Reboot

## 🔨 Build

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew assembleRelease
```

## 📄 License

[GPL-3.0](LICENSE)
