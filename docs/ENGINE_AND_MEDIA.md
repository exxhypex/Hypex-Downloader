# Engine, Extraction & Media Processing Logic

HYPEX separates media extraction and transfer orchestration into two complementary layers:
1. **Active Android Runtime**: `NativeMediaEngine` (OkHttp extraction + resumable HTTP transfers) and `MediaProcessor` (hardware `MediaExtractor` / `MediaMuxer` container operations).
2. **Standalone Python `yt-dlp` Package**: Modular Python package under `app/src/main/python/downloader/` tested via `unittest`, providing `yt-dlp` option construction, format selection rules, Netscape cookie validation, progress throttling, cancellation hooks, and error classification.

---

## 1. URL Extraction Routing (`NativeMediaEngine.extractMedia`)

When a URL is submitted, `NativeMediaEngine` classifies and routes it in strict order:

```text
  Input URL
      │
      ├──► Matches YouTube Video Regex? ──(Yes)──► extractYouTubeMetadata()
      │    (watch?v=, embed/, v/, shorts/, youtu.be/)   └─ oEmbed JSON + 5 formats + subtitles
      │
      ├──► Contains "playlist" or "list="? ─(Yes)─► extractPlaylist()
      │                                                  └─ HTML watch?v= scrape (up to 50 items)
      │
      ├──► Ends with .mp4/.m4a/.mp3/.webm? ─(Yes)─► Direct Stream MediaMetadata
      │
      └──► Fallback ──────────────────────────────► HTML Fetch + OpenGraph (<meta og:*>) Scrape
```

### Route Details
- **YouTube Video ID Regex**: Extracts 11-character IDs across `youtube.com/watch?v=`, `embed/`, `v/`, `shorts/`, and `youtu.be/`. Metadata is fetched from `https://www.youtube.com/oembed?format=json` for real video title and creator name, paired with `maxresdefault.jpg` thumbnails and standard video (`1080p`, `720p`, `480p`) and audio (`m4a`, `mp3`) format descriptors plus subtitle options (`en`, `es`, `auto`).
- **Playlist Extraction**: Fetches playlist HTML with a mobile `User-Agent`, parses `<title>` and up to 50 unique `/watch?v=<id>` links into `PlaylistItem` objects, and falls back to 5 generated test tracks if HTML parsing yields zero items (e.g., offline or JS-only pages).
- **Direct Media Links**: Immediately builds a `MediaMetadata` payload with `direct_stream` format metadata without waiting for an HTML scrape.
- **Generic Web Media**: Parses `og:title` (falling back to `<title>`), `og:image`, and `og:site_name` from the target HTML document and exposes `best` (`mp4`) and `audio_mp3` options.

> [!NOTE]
> In `resolveDirectStreamUrl(...)`, direct `.mp4`, `.mp3`, and `.m4a` URLs are downloaded verbatim from their host. Non-direct YouTube watch URLs currently route to public sample CDN streams (`ForBiggerBlazes.mp4` / `outdoor_ambience.ogg`) in the Kotlin fallback layer when an embedded Python runtime is not bridged. See [Known Limitations](BUILD_CI_AND_TROUBLESHOOTING.md#4-known-limitations--architecture-notes).

---

## 2. Resumable Download Pipeline (`NativeMediaEngine.startDownload`)

Every download runs on `Dispatchers.IO` with concurrent-safe pause/cancel tracking via `ConcurrentHashMap.newKeySet<Long>()`.

### Step-by-Step Transfer Logic
1. **Filename Sanitization**: Strips filesystem-unsafe characters `[\/:*?"<>|]` from the media title, truncates to 80 characters, and appends `_<taskId>.<ext>` (`mp3`, `m4a`, `webm`, or `mp4`).
2. **Partial Cache Inspection**: Checks `cacheDir/temp_<taskId>.<ext>`. If bytes already exist on disk (`downloadedBytes > 0`), attaches header `Range: bytes=$downloadedBytes-`.
3. **Range Rejection Fallback**: If the server does not return `2xx` or `206 Partial Content`, resets `downloadedBytes = 0L` and deletes the stale temporary file before writing from offset `0`.
4. **Random-Access Streaming**: Opens `RandomAccessFile(tempFile, "rw")`, calls `raf.seek(downloadedBytes)`, and reads in `16 KB` chunks (`ByteArray(16 * 1024)`).
5. **Lock-Free Control Checks**: On every read iteration:
   - If `cancelledTasks.contains(taskId)`: closes `RandomAccessFile`, deletes `tempFile`, and returns failure `"DOWNLOAD_CANCELLED"`.
   - If `pausedTasks.contains(taskId)`: closes `RandomAccessFile` **without deleting `tempFile`** so future resume continues from `tempFile.length()`, and returns failure `"DOWNLOAD_PAUSED"`.
6. **Throttled Telemetry (350ms)**: Computes instantaneous speed (`bytesSinceLastReport * 1000 / elapsed`), remaining ETA, and clamped progress (`0f..100f`) at most once every 350ms to prevent Room/UI flooding.
7. **Atomic Finalization & MediaStore**: Moves `tempFile` to `filesDir`, invokes `publishToMediaStore(...)` on Android 10+, and emits a final `100f` `COMPLETED` progress event.

---

## 3. Native Hardware Media Processing (`MediaProcessor`)

`MediaProcessor` provides zero-subprocess container remuxing and audio extraction powered by Android's `MediaExtractor` and `MediaMuxer`:

- **`mergeVideoAndAudio(videoFile, audioFile, outputFile)`**:
  - Scans `MediaExtractor` tracks for the first `video/*` and `audio/*` MIME streams.
  - Initializes `MediaMuxer(MUXER_OUTPUT_MPEG_4)` and copies compressed sample frames directly through a `1 MB` `ByteBuffer` without re-encoding.
- **`extractAudio(videoFile, outputAudioFile)`**:
  - Isolates the `audio/*` track from a video container and muxes it cleanly into `.m4a`.
- **Sample-to-Buffer Flag Mapping (`mapSampleFlagsToBufferFlags`)**:
  - Translates `MediaExtractor.SAMPLE_FLAG_SYNC` → `MediaCodec.BUFFER_FLAG_KEY_FRAME` and `MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME` → `MediaCodec.BUFFER_FLAG_PARTIAL_FRAME` to satisfy Android Lint's `@BufferInfo.Flags` contract and ensure valid MP4 keyframe indexing.

---

## 4. Python `yt-dlp` Engine Package (`app/src/main/python/downloader/`)

The repository includes a complete, unit-tested Python `yt-dlp` module suite designed around a singleton runtime model:

| Module | Responsibilities & Internal Logic |
| :--- | :--- |
| **`engine.py` (`HypexEngine`)** | Thread-safe singleton (`threading.Lock`) that lazily imports `yt_dlp.YoutubeDL`, builds option dicts (`continuedl`, `nocheckcertificate`, `cookiefile`, `progress_hooks`), exposes `extract_info()`, `download_media()`, `cancel_task()`, and returns JSON payloads. |
| **`extractor.py` (`MediaExtractor`)** | Detects playlists (`_type == "playlist"` or `"entries"`), truncates long descriptions at 300 chars, picks fallback thumbnails, and delegates format/subtitle normalization. |
| **`formats.py` (`FormatManager`)** | Filters out non-video storyboards (`mhtml`, `webp`), classifies streams (`is_video_only`, `is_audio_only`, `is_muxed`), sorts descending by `(height, tbr)`, and generates `yt-dlp` selector strings for presets (`Best`, `4K`, `2K`, `1080p`, `720p`, `480p`, `MP3`, `M4A`). |
| **`playlists.py` (`PlaylistExtractor`)** | Normalizes playlist IDs, uploader fallback chains, 1-indexed track positions, and per-item `selected = True` defaults. |
| **`cookies.py` (`CookieManager`)** | Validates Netscape/Mozilla HTTP cookie files (ignoring `#` comments, requiring at least one tab-separated row with `>= 7` columns) and stores `hypex_cookies.txt` in private app storage. |
| **`progress.py` (`ProgressHookHandler`)** | Throttles `yt-dlp` progress callbacks to `300ms` (except `finished`), computes clamped percentage from `total_bytes` or `total_bytes_estimate`, and raises `"DOWNLOAD_CANCELLED"` when cancellation is signaled. |
| **`cancellation.py` (`CancellationController`)** | Thread-safe singleton storing active cancel requests in a locked Python `set`. |
| **`subtitles.py` (`SubtitleManager`)** | Combines manual `subtitles` and `automatic_captions` maps into unified language/format records. |
| **`postprocess.py` (`PostProcessorManager`)** | Composes `yt-dlp` FFmpeg postprocessor chains (`FFmpegExtractAudio`, `FFmpegVideoRemuxer`, `FFmpegEmbedSubtitle`, `FFmpegMetadata`). |
| **`errors.py` (`classify_error`)** | Maps raw exception strings into structured error codes: `VIDEO_UNAVAILABLE`, `AGE_RESTRICTED`, `GEO_RESTRICTED`, `NETWORK_ERROR`, `FORMAT_NOT_AVAILABLE`, `DOWNLOAD_CANCELLED`, and `EXTRACTION_ERROR`. |
