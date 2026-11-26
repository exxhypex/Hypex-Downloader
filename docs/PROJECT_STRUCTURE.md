# Project structure

Hypex Downloader remains a single Android application module. The source tree is grouped by responsibility without introducing extra Gradle modules. Kotlin sources use the project namespace `com.exxhypex.hypexdownloader`.

```text
app/src/main/
├── AndroidManifest.xml
├── java/com/exxhypex/hypexdownloader/
│   ├── MainActivity.kt
│   ├── HypexApp.kt
│   ├── data/
│   │   ├── DownloadRepository.kt
│   │   └── database/
│   │       ├── AppDatabase.kt
│   │       ├── Daos.kt
│   │       └── Entities.kt
│   ├── download/
│   │   ├── bridge/
│   │   ├── media/
│   │   ├── network/
│   │   ├── service/
│   │   └── updates/
│   ├── model/
│   │   └── Models.kt
│   └── ui/
│       ├── home/
│       ├── downloads/
│       ├── playlists/
│       ├── settings/
│       ├── cookies/
│       ├── extraction/
│       ├── formatselector/
│       ├── components/
│       └── theme/
├── python/downloader/
└── res/
```

## Boundaries

- `data/`: persistence and repository access.
- `download/`: downloader engines, media processing, background service, recovery and engine updates.
- `model/`: shared application data models.
- `ui/`: Compose screens, dialogs, reusable components and theme.
- `python/downloader/`: embedded yt-dlp implementation and its Python helpers.

The refactor updates Kotlin package declarations and source paths to the project namespace. The published application ID remains `com.hypex.downloader` so existing installs retain the same app identity. Public APIs, dependencies, manifest component names, Python paths and runtime behavior are otherwise unchanged. Keep this as one Gradle module until there is a concrete need for independent module builds or ownership.
