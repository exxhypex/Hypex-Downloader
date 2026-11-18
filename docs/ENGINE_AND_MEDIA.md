# Engine, Extraction & Media Processing Logic

HYPEX now uses a hybrid engine architecture:

1. Embedded yt-dlp runtime: Chaquopy bundles Python 3.13 and the yt-dlp package inside the Android app. PythonYtDlpEngine calls the existing Python package through downloader.android_bridge.
2. Native direct-stream engine: NativeMediaEngine uses OkHttp and Android storage APIs for direct media URLs.
3. Hybrid routing: HybridMediaEngine sends direct file URLs to OkHttp and supported website URLs, including YouTube and playlists, to yt-dlp. The legacy Kotlin oEmbed/sample-stream path is no longer used for website downloads.
4. Progress and persistence: the foreground service coalesces frequent engine callbacks, persisting progress at most once per second and refreshing the notification at most every 1.5 seconds (except terminal events).

## Embedded Python runtime

- Chaquopy 17.2.0 is configured for Android Gradle Plugin 9.x and the app minimum API 24.
- Python 3.13 and yt-dlp are packaged into the APK for arm64-v8a and x86_64.
- Runtime startup is lazy: Python initializes on the first extraction or site download, off the main thread.
- Python uses the app private filesDir for cookies and download output; it no longer assumes a hard-coded package data path.
- TLS certificate validation is enabled. Extraction errors are surfaced instead of being silently ignored.
- Format catalogs are sorted to prefer ready-to-play muxed streams and capped at 60 visible rows to reduce UI work.

## URL routing

| Input | Engine |
| --- | --- |
| Direct .mp4, .m4a, .mp3, .webm, .mkv, .ogg, .opus, .wav | Native OkHttp engine |
| YouTube video / Shorts / playlist | Embedded yt-dlp |
| Other supported yt-dlp sites | Embedded yt-dlp |

The Python bridge returns normalized media, format, playlist, and subtitle data to Kotlin models. Download callbacks are translated to DownloadProgress and persisted by the foreground service.

## Current Phase One limitations

- A native MediaMuxer path now supports remuxing separate compatible video/audio tracks into MP4 and extracting an existing audio track into M4A without transcoding. It interleaves samples by presentation timestamp, sizes buffers from track metadata, and removes partial output after failures. It does not transcode codecs or create MP3 files; unsupported codecs/containers still require a future conversion backend. The current default yt-dlp presets continue to prefer single-file streams.
- Pause currently interrupts yt-dlp and relies on yt-dlp partial-file continuation when the task is resumed. It is not yet a fully checkpointed pause protocol.
- The standalone engine updater still manages its existing downloaded executable package; it does not replace the embedded Python package in this phase.
- Runtime network extraction and device performance still need CI and real-device validation. A successful source change is not evidence that every supported site works.

## Performance changes

- Main destination navigation no longer crossfades two large screen trees simultaneously.
- Format lists are bounded to 60 entries and prioritize muxed formats.
- Progress persistence is throttled to one write per second, while notification updates are throttled to 1.5 seconds.
- Python startup and yt-dlp work run on Dispatchers.IO, not the UI thread.
