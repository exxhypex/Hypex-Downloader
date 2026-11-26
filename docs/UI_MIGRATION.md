# HYPEX × Miuix Glass UI migration

## Strategy

Migrate the existing Compose UI screen by screen. Keep Material 3 available for screens that have not migrated yet. UI work must not change the downloader engine, Room schema, Python/yt-dlp bridge, foreground service, media merge pipeline, permissions, or application ID.

## Dependency baseline

- Miuix Glass: `top.yukonga.miuix.kmp:miuix-glass-android:0.9.5-beta04`
- Kotlin: 2.4.21
- Android Gradle Plugin: 9.5.0
- Gradle wrapper: 9.7.1
- KSP: 2.3.12
- Jetpack Compose BOM: 2026.11.01

Miuix is experimental. Keep its version pinned and update only after validating the API and build.

## Android compatibility

The app keeps `minSdk 24`. Miuix Glass targets API 24, while its shader blur helper declares API 33. The manifest merger override is limited to the blur library, and Miuix's `glassPanel` checks RuntimeShader support before drawing blur. API 33+ gets shader glass; earlier supported Android versions use the explicit opaque HYPEX surface fallback.

## Screen rollout

1. Foundation + Home: shared Miuix Glass panel wrapper, dedicated backdrop layer, Home extraction panel, active-download banner, and recent-download cards.
2. Downloads: progress cards and action surfaces.
3. Playlists: list surfaces and selection dialogs.
4. Settings: preference groups and controls.
5. Extraction, format, and cookie dialogs: Miuix glass dialogs and fields.
6. Remove Material 3 only after every remaining screen and regression test has migrated.

## Regression requirements

- Keep Home URL entry, paste, clear, extraction, format selection, quick presets, recent-download navigation, and active-download navigation functional.
- Keep queueing, sequential video/audio download and merge, pause/resume/cancel/retry, playlists, cookies, storage, permissions, engine updates, and recovery behavior unchanged.
- Run Python unit tests, Android unit tests, lint, debug APK, and release APK builds for each migration PR.
- Verify the API 24 opaque fallback and API 33+ shader blur before considering the Home phase complete.
