# Android Shell

Minimal native Android WebView shell for Hanako Mobile Lite V0.1.

## Baseline

- Android 16 / API 36.1
- `compileSdk = 36`
- `compileSdkMinor = 1`
- `targetSdk = 36`
- `minSdk = 24`, retained only as a compatibility path
- JDK 17
- Gradle 8.14.5
- Android Gradle Plugin 8.13.2

## Target URL

```text
http://192.168.31.11:14500/mobile/
```

The shell allows only this host, port, and `/mobile` path family.

## Build

```powershell
.\gradlew.bat assembleDebug --no-daemon
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Interaction Notes

- Normal page refresh uses a bottom pull-up gesture: scroll the conversation to the bottom, then keep pulling up.
- The visible refresh button experiments were removed to avoid overlapping the send button and workspace button.
- Error state still provides a native retry button.

## V0.1 Validation

Passed on a real Android 16 target phone:

- A: refresh / WebView restart restores previous session
- B: background-to-foreground catches up messages
- C: network reconnect catches up messages
