# Release Notes: Hanako Mobile Lite V0.1.5

## Scope

V0.1.5 is a UX polish release building on V0.1.

Focus: immersive display, smooth interactions, proper app identity.

## Changes from V0.1

### Immersive Display
- Enabled Edge-to-Edge mode: content extends behind status bar and navigation bar
- Status bar and navigation bar are now transparent
- Injected CSS custom properties for safe-area insets (`--hana-safe-top`, etc.)

### Pull-to-Refresh Animation
- Replaced hard `location.reload()` with animated pull-to-refresh
- Rotating spinner indicator appears on pull-down
- Indicator follows finger position, shows "下拉刷新" / "松手刷新" states
- Spinner animation plays before reload triggers
- Smooth transitions with cubic-bezier easing on release

### Back Gesture Navigation
- Back press now tries "go back to chat list" via JS before falling back to WebView history
- Only exits the app when already at the top-level list view
- JS layer monitors sidebar visibility to determine navigation depth

### Sidebar Keep-Open
- Sidebar (session list) remains visible after selecting a conversation
- MutationObserver watches for sidebar being hidden and restores visibility
- Supports multiple DOM structure patterns via heuristic selector matching

### App Icon
- Custom launcher icon using user avatar image
- All Android density buckets: mdpi (48px) through xxxhdpi (192px)
- Adaptive icon support (API 26+) with foreground layer and background color
- Round icon variant included

## Known Constraints
- Sidebar keep-open and back navigation depend on mobile web UI DOM structure
- Heuristic selectors may need tuning if the web UI is updated
- Target URL remains hardcoded
- Debug signing only

## Baseline
Same as V0.1:
- Android 16 / API 36.1
- compileSdk 36 + compileSdkMinor 1
- Gradle 8.14.5 / AGP 8.13.2 / Kotlin 2.0.20 / JDK 17

## Acceptance
Pending real-device validation.
