# In-App `yt-dlp` Updater & APK Signing

HYPEX Downloader includes a dedicated dual-channel (`Stable` + `Nightly`) in-app `yt-dlp` updater (`EngineUpdater`) with cryptographic verification, staged atomic swapping, and instant rollback, alongside deterministic debug and release APK signing.

---

## 1. Dual Release Channels (`UpdateChannel`)

Users can switch release tracks at any time inside **Settings → Release Channel**. Channel preference is persisted in `SharedPreferences` (`hypex_engine_updater_prefs` under key `preferred_update_channel`, defaulting to `STABLE`).

| Channel | GitHub Repository | Update Cadence | Use Case |
| :--- | :--- | :--- | :--- |
| **`STABLE`** | `yt-dlp/yt-dlp` | Periodic tested releases | Recommended everyday extraction stability |
| **`NIGHTLY`** | `yt-dlp/yt-dlp-nightly-builds` | Daily master builds | Rapid fixes when upstream sites change extractors |

### Version & Channel-Switch Detection
1. Queries `https://api.github.com/repos/<repoSlug>/releases/latest` with `Accept: application/vnd.github.v3+json`.
2. Strips leading `v` from `tag_name` and inspects release `assets`:
   - Prefers standalone zipapp asset named **`yt-dlp`**.
   - Falls back to **`yt-dlp.tar.gz`** if `yt-dlp` is absent.
   - Captures **`SHA2-256SUMS`** URL whenever attached to the release.
3. Evaluates `hasUpdate` via `isVersionDifferentOrNewer(...)`:
   - If the selected channel differs from the currently installed channel (`isChannelSwitch == true`), an update/install is **always offered** so users can seamlessly move between Stable and Nightly builds regardless of timestamp ordering.
   - Otherwise, splits version strings on `.`, `-`, and `_` and performs segment-by-segment numeric comparison (`compareVersions`).

---

## 2. Cryptographic Verification & Atomic Swap Pipeline

`EngineUpdater.downloadAndInstallUpdate(...)` drives a reactive `UpdateInstallState` state machine (`Idle` → `Checking` → `Downloading` → `Verifying` → `Installing` → `Success` / `Error`):

```text
  GitHub Release Asset + SHA2-256SUMS
        │
        ▼ (1. Download over HTTPS)
  filesDir/ytdlp_engine/staging/yt-dlp.tmp
        │
        ▼ (2. Magic-Byte & Size Gate)
  Size >= 64 KB AND (ZIP "PK\x03\x04" in first 512B OR GZIP "\x1F\x8B")
        │
        ▼ (3. Digest Verification)
  Compute SHA-256 of yt-dlp.tmp ══ Match entry in SHA2-256SUMS?
        │ (Mismatch throws SecurityException & deletes staging file)
        ▼ (4. Snapshot Backup)
  filesDir/ytdlp_engine/current/* ──copy──► filesDir/ytdlp_engine/backup/*
        │
        ▼ (5. Atomic Swap + Metadata Write)
  staging/yt-dlp.tmp ──rename/copy──► current/yt-dlp + current/metadata.json
```

### Security & Integrity Guarantees
- **Minimum Size & Magic Header Check (`isValidEnginePayload`)**: Rejects truncated HTML error pages or corrupt files under `64 KB`. Checks the first `512` bytes for ZIP magic (`0x50 0x4B 0x03 0x04`—matching standard Python zipapps prefixed with `#!/usr/bin/env python3\n`) or GZIP magic (`0x1F 0x8B` for `.tar.gz`).
- **SHA-256 Verification (`parseSha256Sums` + `computeSha256`)**: Parses standard checksum lines (`<64-hex-chars>  [*|./]<assetName>`), computes the actual SHA-256 digest of the staged file in `16 KB` chunks, and aborts with `SecurityException` on any mismatch or missing asset entry—preserving the existing active installation untouched.
- **Metadata Tracking**: Records `version`, `channel`, `sha256`, `asset_name`, `installed_at`, and `source_url` in `current/metadata.json`.
- **Live Engine Diagnostics**: `NativeMediaEngine.getEngineVersion()` and `getEngineDiagnostics()` read directly from `EngineUpdater.getInstalledState()`, surfacing the active version, channel, package digest prefix, and rollback snapshot in the Diagnostics modal.

### Rollback & Bundled Reset
- **One-Tap Rollback (`rollback()`)**: Restores `backup/yt-dlp` and `backup/metadata.json` back into `current/`, updates the preferred channel to match the restored build, and deletes the consumed backup slot.
- **Reset to Bundled (`resetToBundled()`)**: Recursively removes `current`, `backup`, and `staging` under `filesDir/ytdlp_engine/` and reverts to the baseline bundled version **`2026.03.01`** on the `STABLE` channel.

---

## 3. Android APK Signing & Keystore Configuration

`app/build.gradle.kts` defines two explicit `signingConfigs`:

### Debug Signing (`debugConfig`)
- Uses `${rootDir}/debug.keystore` with standard debug credentials (`storePassword = "android"`, `keyAlias = "androiddebugkey"`, `keyPassword = "android"`).
- In CI, if `debug.keystore.base64` is present it is decoded automatically; otherwise the workflow generates a fresh `2048-bit` RSA `debug.keystore` via `keytool` before running tests and debug builds.

### Release Signing (`release`)
Reads keystore location and credentials from environment variables so secrets are never committed to source control:

| Environment Variable | Fallback / Requirement |
| :--- | :--- |
| `KEYSTORE_PATH` | Defaults to `${rootDir}/my-upload-key.jks` if unset |
| `STORE_PASSWORD` | Must match the upload keystore password |
| `KEY_PASSWORD` | Must match the `upload` key alias password |
| *Key Alias* | Fixed to `"upload"` in `app/build.gradle.kts` |

- **GitHub Actions CI Secrets**: Configure optional repository secrets `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD` to sign release APKs with your production upload key. When those secrets are absent (such as on forks or initial setup), the CI workflow generates an ephemeral `upload` keystore in `runner.temp` so `:app:assembleRelease` still compiles and produces a verifiable release APK artifact.
