# UI, AMOLED Theme & User Workflows

HYPEX Downloader is built with **Jetpack Compose** and **Material Design 3**, styled around an AMOLED-first dark surface system with translucent glassmorphic cards and high-contrast neon accents.

---

## 1. Visual Design System & Color Tokens

Defined centrally in `Color.kt`, `Theme.kt`, and `Type.kt`:

| Token | Hex Value | Usage |
| :--- | :--- | :--- |
| `AmoledBlack` | `#05070B` | Primary app scaffold background |
| `SurfaceDark` | `#0D111A` | Bottom navigation bar, modal sheets, and alert dialogs |
| `SurfaceCard` | `#121824` | Translucent glass cards and format chips |
| `SurfaceVariantDark` | `#161D2B` | Text fields, progress tracks, and secondary badges |
| `SurfaceCardBorder` | `#222C3F` | `1.dp` crisp card borders |
| `NeonCyan` | `#00F0FF` | Primary CTAs, active download indicators, video badges |
| `ElectricViolet` | `#A855F7` | Nightly channel accent, playlist actions, audio badges |
| `AccentGreen` | `#10B981` | Completed tasks, verified SHA-256 & Stable badges |
| `AccentAmber` | `#F59E0B` | Paused state & rollback controls |
| `AccentRed` | `#EF4444` | Errors, cancellation, destructive delete actions |
| `TextPrimary` / `Secondary` / `Muted` | `#F8FAFC` / `#94A3B8` / `#64748B` | Typographic hierarchy |

- **Edge-to-Edge Compliance**: `MainActivity` invokes `enableEdgeToEdge()`, and `MainScreen`'s `NavigationBar` applies `navigationBarsPadding()` while `Scaffold` passes `innerPadding` cleanly to each screen.
- **Reusable Surface Components**: `GlassCard` and `StatusBadge` standardize border styling, rounded corners (`12–20.dp`), and semantic status pills.

---

## 2. Navigation Shell & Screen Flows

`MainScreen` manages a four-destination `Crossfade` bottom navigation bar (`AppDestination`) with a live `BadgedBox` count showing active `DOWNLOADING` tasks on the **Downloads** tab:

```text
┌───────────┐   ┌───────────────┐   ┌─────────────┐   ┌────────────┐
│   Home    │   │   Downloads   │   │  Playlists  │   │  Settings  │
└─────┬─────┘   └───────┬───────┘   └──────┬──────┘   └─────┬──────┘
      │                 │                  │                │
      ▼                 ▼                  ▼                ▼
 Paste/Share URL   Filter Tabs        Playlist URL     Stable/Nightly
 Quick Presets     Pause/Resume       Select Tracks    SAF Cookies
 Extract Sheet     Open/Share/Retry   Batch Queue      Diagnostics
```

### 1. Home (`HomeScreen`)
- **Hero Header & Stats Pill**: Displays the gradient HYPEX badge and total completed downloads count.
- **Active Download Live Banner**: Appears automatically whenever a task is `DOWNLOADING`, showing live progress bar, downloaded/total bytes, transfer speed, ETA, and tap-to-open Downloads navigation.
- **URL Input & Clipboard Quick-Paste**: Includes a clear action and a one-tap **Paste** button (`ClipboardManager`) that pastes the clipboard text and immediately invokes extraction.
- **Quick Format Presets**: Displays the 6 built-in presets (`Best Quality`, `1080p MP4`, `720p Balanced`, `Data Saver`, `Audio MP3`, `Audio M4A`).
- **Supported Platforms & Recent Downloads**: Lists supported platforms/protocols and the 3 most recent tasks.

### 2. Extraction Bottom Sheet (`ExtractionBottomSheet`) & Stream Inspector (`FormatSelectorDialog`)
- Triggered automatically whenever `_extractedMedia` is populated (from Home or via `ACTION_SEND`).
- Shows high-res thumbnail, formatted duration badge, creator, and stream count.
- **Video & Audio / Audio Only Tabs**: Switches available format chips and target container options (`mp4`/`mkv`/`webm` vs. `mp3`/`m4a`/`opus`/`wav`).
- **Inspect All Streams & Codecs**: Opens `FormatSelectorDialog`, displaying raw `formatId`, `vcodec`, `acodec`, container badge, and estimated filesize for every extracted stream.
- **Queue Download**: Enqueues the task into Room and starts `DownloadForegroundService`.

### 3. Downloads (`DownloadsScreen`)
- **Status Filter Chips**: `ALL`, `DOWNLOADING` (includes `PROCESSING`), `QUEUED` (includes `PAUSED`), `COMPLETED`, and `FAILED` (includes `CANCELLED`).
- **Contextual Task Actions**:
  - `DOWNLOADING`: **Pause**, **Cancel**
  - `PAUSED`: **Resume**, **Cancel**
  - `COMPLETED`: **Open File** (via `FileProvider` + `ACTION_VIEW`), **Share File** (`ACTION_SEND`), **Delete Record**
  - `FAILED` / `CANCELLED`: **Retry**, **Delete Record**
- **Clear History**: Confirmation dialog that removes finished/failed/cancelled entries from Room without deleting downloaded media files on disk.

### 4. Playlists (`PlaylistsScreen`)
- Accepts playlist URLs, triggers `extractPlaylist`, and renders playlist metadata alongside **Select All / Deselect All** controls and per-track checkboxes.
- **Download Selected Videos**: Saves the playlist and items to Room (`playlists` + `playlist_items`) and enqueues each selected video individually into `DownloadForegroundService`.

### 5. Settings & Engine (`SettingsScreen`) & Cookie Manager (`CookieManagerDialog`)
- **yt-dlp Core Engine Card**: Shows active version, channel badge (`STABLE` / `NIGHTLY`), SHA-256 package digest prefix, Stable/Nightly channel switcher, update check button, release notes/size preview, live download/verification/install progress UI, **Rollback**, and **Bundled** restore buttons.
- **Netscape Cookie Manager (`CookieManagerDialog`)**: Uses zero-permission Android Storage Access Framework (`ActivityResultContracts.OpenDocument`) to import `cookies.txt` for restricted media, with live configured status and one-tap deletion.
- **Network, Battery & System Info**: Wi-Fi-only preference toggle, OS battery optimization guidance modal, live **Engine Diagnostics** dialog, and Open Source Licenses dialog.

---

## 3. UI Testability & Accessibility

- Primary interactive controls carry explicit `Modifier.testTag(...)` identifiers (e.g., `nav_home`, `nav_downloads`, `nav_playlists`, `nav_settings`, `url_input_field`, `paste_button`, `extract_media_button`, `start_download_button`, `playlist_url_input`, `extract_playlist_button`, `download_playlist_selection_button`, `channel_stable_button`, `channel_nightly_button`, `check_updates_button`, `install_update_button`, `rollback_engine_button`, `reset_bundled_button`).
- Directional icons use `Icons.AutoMirrored.Filled.*` (including `PlaylistPlay`), and primary interactive buttons meet Material 3 minimum `48.dp` height touch targets.
