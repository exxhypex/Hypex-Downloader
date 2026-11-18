# HYPEX Downloader

[![HYPEX Downloader Android CI](https://github.com/exxhypex/Hypex-Downloader/actions/workflows/android.yml/badge.svg)](https://github.com/exxhypex/Hypex-Downloader/actions/workflows/android.yml)
![Platform](https://img.shields.io/badge/Platform-Android%20(API%2024%E2%80%9336)-00F0FF?style=flat-square)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20M3-A855F7?style=flat-square)
![ABI](https://img.shields.io/badge/ABI-arm64--v8a%20%7C%20x86__64-10B981?style=flat-square)

**HYPEX Downloader** (`com.hypex.downloader`) is a native Android media extraction and download manager built with **Kotlin**, **Jetpack Compose**, **Room**, **OkHttp**, **Android MediaMuxer**, and an embedded **Python 3.13 + yt-dlp** runtime powered by Chaquopy.

---

## Documentation Index

Every subsystem and internal logic flow is documented separately for clarity:

| Document | Scope & Covered Logic |
| :--- | :--- |
| [**Architecture & Data Layer**](docs/ARCHITECTURE.md) | Clean MVVM + Repository architecture, `StateFlow` orchestration, `DownloadStatus` state machine, Room SQLite schema, `ACTION_SEND` share-intent handling, `FileProvider`, and `MediaStore` publishing |
| [**Engine, Extraction & Media Logic**](docs/ENGINE_AND_MEDIA.md) | Hybrid URL routing, embedded Python `yt-dlp` extraction/download bridge, direct-stream HTTP `Range` transfers, `MediaExtractor`/`MediaMuxer` processing, and Phase One limitations |
| [**Background Service & Recovery**](docs/BACKGROUND_AND_RECOVERY.md) | `DownloadForegroundService` (`dataSync`), `PARTIAL_WAKE_LOCK`, live notification controls, and `RecoveryManager` crash/process-kill state reconciliation |
| [**In-App yt-dlp Updater & Signing**](docs/UPDATER_AND_SIGNING.md) | Stable vs. Nightly GitHub Release updater, magic-byte validation, `SHA2-256SUMS` verification, atomic `current`/`backup`/`staging` swaps, rollback, and Android keystore signing |
| [**UI, AMOLED Theme & User Flows**](docs/UI_AND_UX.md) | AMOLED dark design tokens, edge-to-edge layout, 4-tab navigation, extraction bottom sheet, stream inspector, playlist batching, presets, and SAF cookie dialog |
| [**Build, Testing, CI/CD & Troubleshooting**](docs/BUILD_CI_AND_TROUBLESHOOTING.md) | Toolchain versions, local Robolectric/Python/Lint commands, GitHub Actions CI pipeline details, known architectural caveats, and troubleshooting |

---

## Key Capabilities

- **Multi-Source Extraction**: Routes YouTube videos, Shorts, playlists, and other supported websites through the embedded yt-dlp runtime; direct media URLs use the lightweight native OkHttp engine.
- **Share Sheet Integration**: Accepts `text/plain` `Intent.ACTION_SEND` links from browsers and other apps to prefill and immediately trigger extraction.
- **Resumable Chunked Downloads**: Uses yt-dlp continuation for supported websites and the native OkHttp `Range` transfer path for direct media URLs.
- **Native Hardware Muxing**: Remuxes separate video/audio tracks and extracts `.m4a` audio streams using Android's `MediaExtractor` and `MediaMuxer` without spawning external binaries.
- **Persistent Queue & Crash Recovery**: Stores downloads, queues, playlists, and presets in **Room SQLite** (`hypex_downloader.db`) and automatically reconciles interrupted tasks on app launch.
- **Background Foreground Service**: Keeps active transfers alive via a `dataSync` foreground service, 10-minute partial wake lock, and live notification **Pause / Resume / Cancel** buttons.
- **Playlist Batch Downloading**: Extracts playlist tracks into selectable lists and enqueues multiple items at once with persisted playlist metadata.
- **Embedded yt-dlp Runtime**: Packages Python 3.13 and yt-dlp inside the APK; initializes lazily on a background dispatcher. The existing standalone updater is not yet connected to this embedded package.
- **AMOLED Dark Interface**: Built with `#05070B` true-black canvas, translucent glassmorphic cards, `#00F0FF` Neon Cyan and `#A855F7` Electric Violet accents.

---

## Phase One Status

Phase One adds the real embedded yt-dlp runtime and hybrid engine routing, removes website downloads from the old sample-stream path, enables TLS certificate verification, bounds format lists, and reduces progress-driven UI work. The media pipeline now includes native MP4 remuxing and lazy LGPL FFmpeg audio conversion for MP3 output. Full video transcoding, complete pause checkpoints, and real-device performance validation remain follow-up work; see [Engine and Media Logic](docs/ENGINE_AND_MEDIA.md).

---

## High-Level System Overview

```text
┌─────────────────────────── Presentation (Jetpack Compose) ───────────────────────────┐
│  MainActivity (Edge-to-Edge, ACTION_SEND Share) ──► HypexViewModel (StateFlows)     │
│  Screens: Home │ Downloads │ Playlists │ Settings │ Extraction Sheet │ Cookie Dialog │
└─────────────────────────────────────────┬────────────────────────────────────────────┘
                                          │
                                          ▼
┌────────────────────────────── Data & Persistence Layer ──────────────────────────────┐
│  DownloadRepository ──► Room SQLite (downloads, queue, playlists, playlist_items,    │
│                                      presets)                                        │
└───────────────┬─────────────────────────────────────────┬────────────────────────────┘
                │                                         │
                ▼                                         ▼
┌────── Background & Recovery ──────┐     ┌─────── Media Engine & Processing ──────────┐
│  HypexApp ──► RecoveryManager     │     │  EngineBridge ──► NativeMediaEngine        │
│  (Startup orphan reconciliation)  │     │   ├─ Extraction: YouTube oEmbed/HTML/Direct│
│                                   │     │   ├─ Download: OkHttp + Range Resume       │
│  DownloadForegroundService        │     │   ├─ MediaProcessor: MediaExtractor/Muxer  │
│  (dataSync, WakeLock, PendingInt) │     │   └─ EngineUpdater: Stable/Nightly SHA-256 │
└───────────────────────────────────┘     └────────────────────────────────────────────┘
```

---

## Tech Stack Baseline

| Component | Technology / Version |
| :--- | :--- |
| **Language** | Kotlin `2.2.10` (Android) + Python `3.11+` (Engine Package & Unit Tests) |
| **UI Toolkit** | Jetpack Compose (BOM `2024.09.00`), Material 3, Coil `2.7.0` |
| **Architecture** | Clean MVVM + Repository + `StateFlow` (`WhileSubscribed(5000)`) |
| **Persistence** | Room `2.7.0` (KSP `2.3.5`) + `SharedPreferences` (Updater channel) |
| **Networking** | OkHttp `4.10.0` (HTTP/2, Redirects, Range Resume) |
| **Media Processing** | Android `MediaExtractor`, `MediaMuxer`, `MediaCodec.BufferInfo` |
| **Android SDK** | `minSdk 24`, `targetSdk 36`, `compileSdk 36.1` |
| **Build System** | Gradle `9.3.1`, AGP `9.1.1`, JDK `21` |
| **Testing** | Robolectric `4.16.1`, JUnit 4, Kotlin Coroutines Test `1.10.2`, Python `unittest` |

---

## Quick Start

```bash
# 1. Run Python engine unit tests
python3 -m unittest discover -s app/src/test/python -p "test_*.py" -v

# 2. Run Android unit & Robolectric tests + Lint + Debug APK
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon

# 3. Build Release APK (uses KEYSTORE_PATH / STORE_PASSWORD / KEY_PASSWORD env vars if set)
./gradlew :app:assembleRelease --no-daemon
```

> [!NOTE]
> For detailed setup, CI workflow behavior, keystore requirements, and current runtime limitations, see [Build, Testing, CI/CD & Troubleshooting](docs/BUILD_CI_AND_TROUBLESHOOTING.md).
