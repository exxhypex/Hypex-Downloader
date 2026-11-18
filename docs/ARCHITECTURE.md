# Architecture & Data Layer

HYPEX Downloader follows a **Clean MVVM + Repository** architecture with unidirectional data flow from SQLite and engine state up into Jetpack Compose, and command intents flowing down through `HypexViewModel`.

---

## 1. Layer Topology

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                             UI / Presentation                           │
│  MainActivity ──► MainScreen ──► Home / Downloads / Playlists / Settings│
│       ▲                                       │                         │
│       │ ACTION_SEND                           ▼                         │
│       └─────────────────────────────── HypexViewModel                   │
│                                  (StateFlow + AndroidViewModel)         │
└───────────────────────────────────────┬─────────────────────────────────┘
                                        │
                    ┌───────────────────┴───────────────────┐
                    ▼                                       ▼
┌────────────────────────────────────┐   ┌────────────────────────────────┐
│          Data Repository           │   │        Background Layer        │
│  DownloadRepository                │   │  DownloadForegroundService     │
│   ├─ Room DAOs (downloads, queue,  │   │  RecoveryManager (on startup)  │
│   │  playlists, presets)           │   └────────────────┬───────────────┘
│   └─ EngineBridge interface        │                    │
└───────────────────┬────────────────┘                    │
                    └──────────────────┬──────────────────┘
                                       ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                            Engine & Storage                             │
│  NativeMediaEngine │ MediaProcessor │ EngineUpdater │ MediaStore / SAF  │
└─────────────────────────────────────────────────────────────────────────┘
```

### Dependency Rules
- **Composables** never invoke `OkHttpClient`, `AppDatabase`, or `EngineUpdater` directly; all mutations go through `HypexViewModel`.
- **`HypexViewModel`** exposes read-only `StateFlow` streams backed by `viewModelScope` (`SharingStarted.WhileSubscribed(5000)` for Room queries).
- **`DownloadRepository`** abstracts Room DAOs and `EngineBridge`, mapping database entities (`DownloadEntity`) into immutable domain models (`DownloadTask`).
- **`EngineBridge`** decouples download orchestration from the concrete extraction/network engine (`NativeMediaEngine`).

---

## 2. Entry Points & Share-Intent Ingestion

1. **Application Startup (`HypexApp`)**:
   - Invokes `RecoveryManager(this).recoverOnStartup()` inside `Application.onCreate()` on `Dispatchers.IO`.
   - Reconciles orphaned downloads left in `DOWNLOADING` or `PROCESSING` from an abrupt process termination and initializes internal cache/download directories.
2. **Activity Launch (`MainActivity`)**:
   - Calls `enableEdgeToEdge()` and binds `HypexViewModel` via `by viewModels()`.
   - Inspects both `onCreate` `intent` and `onNewIntent` for `Intent.ACTION_SEND` with MIME type `text/plain`.
   - When shared text is present, it trims the payload, updates `_urlInput`, and automatically calls `viewModel.extractUrl(...)` to open extraction immediately.

---

## 3. Domain State Machine (`DownloadStatus`)

Every task tracked by HYPEX transitions through explicit states defined in `DownloadStatus`:

```text
                ┌──────────────┐
                │    QUEUED    │◄───────────────────────────┐
                └──────┬───────┘                            │
                       │ Service picks up task              │ retryDownload()
                       ▼                                    │
                ┌──────────────┐   PAUSED (user / startup)  │
          ┌─────│ DOWNLOADING  │──────────────┐             │
          │     └──────┬───────┘              ▼             │
          │            │             ┌──────────────┐       │
          │            │             │    PAUSED    │───────┘
          │            │             └──────┬───────┘ (resume via Range)
          │            │ 100% written       │
          │            ▼                    ▼
          │     ┌──────────────┐     ┌──────────────┐
          │     │  PROCESSING  │     │  CANCELLED   │
          │     └──────┬───────┘     └──────────────┘
          │            │ Published
          │            ▼
          │     ┌──────────────┐
          │     │  COMPLETED   │
          │     └──────────────┘
          │
          │ Unhandled network/stream error
          ▼
   ┌──────────────┐
   │    FAILED    │──────► (retryDownload() resets status to QUEUED)
   └──────────────┘
```

*Note: `EXTRACTING` and `READY` also exist in the `DownloadStatus` enum for pre-download lifecycle modeling; interactive extraction state in the UI is held reactively in `HypexViewModel._isExtracting` and `_extractedMedia`.*

---

## 4. Room Persistence Schema (`hypex_downloader.db`)

Database `AppDatabase` (version `1`, singleton via double-checked locking with `fallbackToDestructiveMigration(false)`) manages five tables:

| Table | Primary Key | Purpose & Key Columns |
| :--- | :--- | :--- |
| `downloads` | `id` (auto-gen `Long`) | Full task record: `url`, `title`, `thumbnail`, `duration`, `formatId`, `formatDescription`, `outputPath`, `temporaryPath`, `status`, `progress`, `downloadedBytes`, `totalBytes`, `speed`, `eta`, `createdAt`, `updatedAt`, `errorCode`, `errorMessage` |
| `queue` | `id` (auto-gen `Long`) | Active queue ordering table (`name`, `position`, `downloadId`, `status`). Entries are automatically purged when tasks reach `COMPLETED`, `FAILED`, or `CANCELLED`. |
| `playlists` | `playlistId` (`String`) | Saved playlist metadata (`title`, `thumbnail`, `videoCount`, `uploader`, `createdAt`) persisted when batch downloads are queued. |
| `playlist_items` | `id` (auto-gen `Long`) | Individual tracks linked via `playlistId` (`videoId`, `title`, `duration`, `thumbnail`, `position`, `url`, `selected`). |
| `presets` | `id` (`String`) | Format preset templates (`name`, `formatSelector`, `container`, `audioFormat`, `subtitleLanguage`, `filenameTemplate`, `isBuiltIn`). |

### Key DAO Queries & Safeguards
- **`DownloadDao.getAllDownloads()`**: Reactive `Flow` sorted by `updatedAt DESC` so recently active or finished tasks surface at the top.
- **`DownloadDao.getActiveAndQueuedTasks()`**: Returns `QUEUED`, `DOWNLOADING`, and `PROCESSING` rows ordered by `createdAt ASC` for recovery checks.
- **`DownloadDao.markCompleted(...)`**: Sets `status = 'COMPLETED'`, `outputPath`, and `progress = 100.0` (floating-point literal `100.0` for strict SQLite/KSP compatibility).
- **`DownloadDao.clearHistory()`**: Deletes only terminal states (`COMPLETED`, `FAILED`, `CANCELLED`), keeping active or paused tasks intact.

---

## 5. Storage, `FileProvider`, and `MediaStore` Strategy

HYPEX uses a two-tier file lifecycle so downloads survive interruptions cleanly and remain accessible both inside the app and in the Android Gallery/Music library:

1. **Chunked Cache Stage**:
   - In-progress downloads write directly to `context.cacheDir/temp_<taskId>.<ext>` via `RandomAccessFile`.
   - Keeping temporary chunks in `cacheDir` allows `Range` resumption without exposing incomplete files to media scanners.
2. **Private Final Destination**:
   - Upon EOF, the temp file is renamed to `context.filesDir/<sanitizedTitle>_<taskId>.<ext>`.
   - `DownloadRepository.deleteDownload(id, deleteFile = true)` deletes the backing file when requested, while `clearHistory()` preserves files on disk.
3. **Public Scoped `MediaStore` Publishing (Android 10+ / API 29+)**:
   - Completed files are copied into `MediaStore.Video.Media.EXTERNAL_CONTENT_URI` (`Movies/HYPEX`) or `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` (`Music/HYPEX`) using `IS_PENDING = 1` during the stream copy and flipped to `IS_PENDING = 0` on completion.
4. **Secure File Open & Share (`FileProvider`)**:
   - Opening or sharing completed files from `DownloadsScreen` resolves content URIs through `androidx.core.content.FileProvider` (`${applicationId}.fileprovider` mapped via `file_paths.xml` covering `files-path`, `cache-path`, and `external-files-path`) with `FLAG_GRANT_READ_URI_PERMISSION`, avoiding `FileUriExposedException` on Android 7+.
