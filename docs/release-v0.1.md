# Release Notes: Hanako Mobile Lite V0.1

## Scope

V0.1 delivers a minimal Android WebView shell for Hanako mobile.

The app is not a native chat client. It is a thin Android wrapper around the existing Hanako mobile web endpoint.

## Baseline

- Android baseline: Android 16 / API 36.1
- `compileSdk`: 36 + `compileSdkMinor`: 1
- `targetSdk`: 36
- `minSdk`: 24, retained only as a compatibility path
- Gradle: 8.14.5
- Android Gradle Plugin: 8.13.2
- Kotlin Android plugin: 2.0.20
- JDK: 17

## Fixed Target URL

```text
http://192.168.31.11:14500/mobile/
```

Only this host/port/path family is allowed by the shell navigation guard.

## Implemented

- Minimal Android project under `android-shell/`
- WebView setup for JavaScript, DOM storage, cookies, and mixed HTTP content
- Native error overlay for unreachable target / timeout
- Retry button on error state
- Back button behavior: WebView history first, app exit second
- Main-frame error flag to avoid `onPageFinished` hiding real load failures
- System bar inset handling for Android 16 display areas
- Bottom pull-up refresh gesture, replacing earlier visible refresh button attempts
- Debug APK build verified
- Real phone installation verified
- In-app A/B/C regression verified

## Deferred

- Native chat UI
- Dynamic endpoint configuration
- Release signing
- File upload/download integration
- Push notifications
- Background keepalive
- App icon polish
- Store/release packaging

## Known Constraints

- URL is hardcoded for the local development LAN.
- The bottom pull-up refresh gesture is injected into the WebView page after load and depends on scroll-container detection.
- File upload/download are intentionally ignored in V0.1.

## Acceptance Result

V0.1 debug APK passed the target phone validation:

- A: session restore after refresh / WebView restart passed
- B: foreground return catch-up passed
- C: network reconnect catch-up passed

Conclusion: V0.1 is ready for source archival / GitHub upload as a debug-stage lightweight Android shell.
