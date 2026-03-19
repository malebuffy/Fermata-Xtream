# Fermata Xtream (Mod)

## Scam Warning
This app is free and open source. If you paid for this app, you were scammed.

## What This Is
Fermata Xtream is a modded fork of **Fermata Media Player** focused on improving IPTV use with Xtream account support inside the TV addon.

Original project: https://github.com/AndreyPavlenko/Fermata

## Main Mods in This Fork

### Xtream account integration (TV addon)
- Added direct Xtream account mode in TV source add/edit flow.
- Supports importing one content type at a time:
	- Channels
	- Movies
	- Series
- Supports server URL + username/password and parsing credentials from full `player_api.php` URLs.
- Added retries/fallback handling for unstable provider responses.

### Series playback improvements
- Series items open like normal list entries (same flow as movies/channels).
- Added season selection dialog with filtered episodes.

### Manual source refresh workflow
- Added source-level **Refresh** in TV source long-press context menu.
- Refresh is manual and source-specific.
- Added progress dialog updates during refresh/import.

### Performance and stability work
- Improved Xtream playlist generation and refresh behavior for large datasets.
- Added cache-clear/reload flow to reduce stale or duplicated source state after refresh.

### UI/branding changes
- App name changed to **Fermata Xtream**.
- About page updated with mod credits:
	- Modded by Vasileios Antoniadis
	- Xtream account support added
- Added modder donation entry (PayPal):
	- https://paypal.me/Vantoniadis

### First-run anti-scam message
- Added one-time first-run warning dialog.
- Message is stored obfuscated in code and decoded at runtime.

## Notes
- This repository is a modification/fork and is not the original upstream project.
- Respect upstream licensing and attribution.
