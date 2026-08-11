# Fermata Xtream Free (Mod)

## Scam Warning
Fermata Xtream Free is free. If you paid for this app, you were scammed.

## What This Is
Fermata Xtream begun as a modded fork of **Fermata Media Player** focused on improving IPTV use with Xtream account support inside the TV addon. 

Original project: https://github.com/AndreyPavlenko/Fermata

This repository contains the **free** open-source build only (-Pfree=true). Premium/rec-only integrations are not included in this source tree.

## Build
`./build.sh`
# or on Windows:
`./build.ps1`


Artifacts are written to dist/ as ermata-free-*.

## Main Mods in This Fork

### Xtream account integration (TV addon)
- Added direct Xtream account mode in TV source add/edit flow.
- Supports importing content types:
	- Channels
	- Movies
	- Series
- Supports server URL + username/password and parsing credentials from full player_api.php URLs.
- Added retries/fallback handling for unstable provider responses.

### Series playback improvements
- Series items open like normal list entries (same flow as movies/channels).
- Added season selection dialog with filtered episodes.
- Recently played folder for Xtream movies/series sources.

### Xtream catch-up / EPG
- Catch-up menu for Xtream live channels when the provider exposes catch-up data.
- Opens archive playback from the channel context menu.

### Manual source refresh workflow
- Added source-level **Refresh** in TV source long-press context menu.
- Refresh is manual and source-specific.
- Added progress dialog updates during refresh/import.

### Performance and stability work
- Improved Xtream playlist generation and refresh behavior for large datasets.
- Added cache-clear/reload flow to reduce stale or duplicated source state after refresh.

### YouTube SponsorBlock
- Optional SponsorBlock integration for YouTube (disabled by default).
- Skips crowd-sourced segments (sponsor, intro/outro, self-promo, interaction reminders, etc.).
- Per-category toggles in YouTube addon settings.
- Fetches segment data from sponsor.ajay.app for the current video.

### YouTube auto highest quality
- Optional setting to auto-select the highest available YouTube playback quality (disabled by default).
- Applies when a new video starts; can be turned off without restarting the activity.

### Opus-MT subtitle translator
- Added Opus-MT as an optional on-device subtitle translation engine (ONNX Runtime).
- Available alongside existing ML Kit translator in SubGen / translation settings.
- Supports many language pairs via downloadable models (cached locally).
- Model cleanup option to delete other downloaded translation models from cache.
- Default translator remains ML Kit for existing setups.

### Mirror / Android Auto improvements
- Simplified FS Fermata Mirror permission flow (step-by-step prompts).
- Video motion / speed guard for safer video presentation while driving.
- Media service Bluetooth auto-start helper for Android Auto connections.

### UI/branding changes
- App name changed to **Fermata Xtream**.
- About page updated for Xtream account support.

## Upstream updates & fixes (from Fermata 2.0.1)
Ported from upstream without changing existing fork behavior:

- **Library updates:** media3 1.10.1, Material 1.14.0, AndroidX Core 1.19.0, Media 1.8.0, WebKit 1.16.0, Guava 33.6.0, libVLC 3.7.2.
- **Audio:** disable Virtualizer on Android 15+ (API 35+) where it is unsupported.
- **Media service:** safer notification receiver registration.
- **YouTube:** do not redirect 	v.youtube.com into the main YouTube addon.
- **Playback:** play from beginning when an item is tapped to start.

## Notes
- This repository is a modification/fork and is not the original upstream project.
- Respect upstream licensing and attribution.
