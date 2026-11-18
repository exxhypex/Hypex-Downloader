# Build, Testing, CI/CD & Troubleshooting

This guide covers local builds, automated test suites, GitHub Actions CI/CD architecture, and known implementation details.

---

## 1. Build & Toolchain Prerequisites

| Requirement | Configured Value |
| :--- | :--- |
| **JDK** | Java `21` (Temurin recommended; Java `11` bytecode target) |
| **Gradle Wrapper** | `9.3.1` (`./gradlew`) |
| **Android Gradle Plugin (AGP)** | `9.1.1` |
| **Kotlin / KSP** | Kotlin `2.2.10` / KSP `2.3.5` |
| **Android SDK** | `compileSdk 36.1` (`platforms;android-36.1`), `build-tools;36.0.0`, `targetSdk 36`, `minSdk 24` |
| **ABI Filters** | `arm64-v8a`, `x86_64` |
| **Python** | Python `3.11+` (for engine unit tests) |

### Secrets & Environment Configuration
- `.env` / `.env.example` are wired through the `secrets-gradle-plugin`. No API keys are required for core media extraction, downloads, or `yt-dlp` updates.
- `googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }` ensures builds succeed cleanly without a `google-services.json` file.

---

## 2. Running Builds & Tests Locally

### Python Engine Unit Tests
Runs the 5 unit tests in `app/src/test/python/test_engine.py` covering `CancellationController`, `FormatManager.build_format_selector`, `classify_error`, `CookieManager.validate_netscape_format`, and `PlaylistExtractor.parse_playlist`:

```bash
python3 -m unittest discover -s app/src/test/python -p "test_*.py" -v
```

### Android Unit & Robolectric Tests
Runs Robolectric SDK 36 tests (`ExampleRobolectricTest` and `EngineUpdaterTest`, which validates Stable/Nightly JSON parsing, channel switching, SHA-256 verification, mismatch rejection, and rollback):

```bash
./gradlew :app:testDebugUnitTest --no-daemon --stacktrace
```

### Android Lint & APK Assembly

```bash
# Run Android Lint checks
./gradlew :app:lintDebug --no-daemon --stacktrace

# Build Debug APK (signed with debug.keystore)
./gradlew :app:assembleDebug --no-daemon --stacktrace

# Build Release APK (reads KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD)
./gradlew :app:assembleRelease --no-daemon --stacktrace
```

---

## 3. GitHub Actions CI/CD Pipeline

Defined in `.github/workflows/android.yml` and triggered on `push` / `pull_request` to `main` or `master` (plus `workflow_dispatch`):

```text
  Push / PR / Dispatch
         │
         ├──► Job 1: python-tests (ubuntu-latest)
         │      ├─ actions/checkout@v7
         │      ├─ actions/setup-python@v7 (Python 3.11)
         │      └─ unittest discover app/src/test/python
         │
         └──► Job 2: android-build (ubuntu-latest)
                ├─ actions/checkout@v7
                ├─ actions/setup-java@v6 (Temurin JDK 21)
                ├─ Resilient bash Android SDK setup (commandlinetools 15859902)
                │    └─ Installs platform-tools, build-tools;36.0.0, platforms;android-36.1
                ├─ Keystore prep (decodes debug.keystore.base64 OR generates debug.keystore)
                ├─ gradle/actions/setup-gradle@v6 (cache-provider: 'basic')
                ├─ ./gradlew :app:testDebugUnitTest
                ├─ ./gradlew :app:lintDebug
                ├─ ./gradlew :app:assembleDebug
                ├─ Release keystore prep (uses Secrets OR ephemeral upload keystore)
                ├─ ./gradlew :app:assembleRelease
                └─ actions/upload-artifact@v7 (Debug & Release APKs)
```

### Key CI Design Choices
- **Direct Bash Android SDK Setup**: Avoids Node 20 deprecation errors and permission failures on `/usr/local/lib/android/sdk` by detecting whether `$ANDROID_HOME` is writable and falling back cleanly to `$HOME/android-sdk` with `commandlinetools-linux-15859902_latest.zip`.
- **Fallback Keystores**: Never blocks PR or fork builds when `debug.keystore.base64` or release signing secrets (`RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`) are absent.

---

## 4. Known Limitations & Architecture Notes

- **Kotlin Runtime vs. Embedded Python (`Chaquopy`)**:
  - This project uses **Gradle `9.3.1`** and **AGP `9.1.1`**. Chaquopy `16.1.0` throws `NoClassDefFoundError: org/gradle/util/VersionNumber` on Gradle 9, so the Chaquopy Gradle plugin is intentionally not applied.
  - At runtime, `NativeMediaEngine` executes extraction, HTTP `Range` downloads, and `MediaMuxer` remuxing natively in Kotlin while the `app/src/main/python/downloader/` package holds the modular Python `yt-dlp` implementation and unit tests.
- **Direct Stream Resolution for Non-Direct URLs**:
  - Direct `.mp4`, `.mp3`, and `.m4a` URLs download directly from their origin URLs.
  - For webpage URLs such as YouTube watch links, metadata (title/author) is fetched from YouTube oEmbed, while `NativeMediaEngine.resolveDirectStreamUrl(...)` currently maps non-direct links to public sample CDN streams when no embedded Python/yt-dlp execution bridge is attached.
- **In-App `yt-dlp` Updater Scope**:
  - `EngineUpdater` downloads, verifies (`SHA2-256SUMS` + magic headers), stages, and manages `yt-dlp` packages (`Stable` and `Nightly`) under `filesDir/ytdlp_engine/` with full rollback/diagnostics support. Executing the downloaded zipapp directly on-device requires wiring a compatible Python runtime bridge in a future release.
- **Wi-Fi Only Toggle State**:
  - The **Download on Wi-Fi Only** switch in `SettingsScreen` is currently local composable state and is not yet persisted to DataStore or enforced inside `DownloadForegroundService`.

---

## 5. Troubleshooting

| Symptom | Likely Cause | Resolution |
| :--- | :--- | :--- |
| **`SHA-256 mismatch` during engine update** | Partial/tampered CDN download or stale mirror cache | Retry the update check; `EngineUpdater` automatically deletes the staged `.tmp` file and leaves the active build untouched. |
| **GitHub API HTTP `403` / rate limit on update check** | Unauthenticated GitHub API rate limit (`60 req/hr` per IP) | Wait for rate-limit reset or switch networks before re-checking `yt-dlp` releases. |
| **Background download paused by OS** | Aggressive OEM battery killer (MIUI, OneUI, ColorOS) | Set Battery Usage for **HYPEX** to **Unrestricted** in Android App Settings (also documented in Settings → *Battery & Background Execution*). |
| **Build error: missing `platforms;android-36.1`** | Local Android SDK lacks Android 16 minor revision `36.1` | Install via `sdkmanager "platforms;android-36.1" "build-tools;36.0.0"` (add `--channel=1` if needed). |
| **Release build fails locally** | `KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_PASSWORD` unset | Supply an upload keystore with alias `upload` via env vars, or build `:app:assembleDebug` for local testing. |
