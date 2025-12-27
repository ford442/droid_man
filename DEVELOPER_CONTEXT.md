# Developer Context & Project Source of Truth

**Objective:** This document serves as the primary technical reference for the DroidMan project. It outlines the architectural intent, key features, complexity hotspots, and known limitations to ensure efficient onboarding and prevent regression.

## 1. High-Level Architecture & Intent

*   **Core Purpose:** DroidMan is an offline-first Android music player that acts as a "smartphone walkman." It plays local MP3/FLAC files and downloads songs from Google Cloud Storage (GCS) into RAM for offline listening during a single session.
*   **Tech Stack:**
    *   **Language:** Java 21 (Note: Incompatible with project's Gradle 8.0 wrapper in some environments).
    *   **Platform:** Android SDK (minSdk: 24, targetSdk: 34).
    *   **Build System:** Gradle 8.0.
    *   **Key Libraries:** ExoPlayer (media playback), AndroidX/Jetpack libraries (AppCompat, RecyclerView, Core).
    *   **StdLib:** `java.net.HttpURLConnection`, `org.xmlpull.v1.XmlPullParser` (no external GCS SDK).
*   **Design Patterns:**
    *   **Clean Architecture (Lite):** Separation of UI (`MainActivity`), Business Logic (`MusicService`), and Data (`Song`, `SongAdapter`).
    *   **Service-Client:** `MainActivity` binds to `MusicService` for background playback.
    *   **Singleton (Implied):** `MusicService` acts as the single source of truth for playback state.
    *   **Adapter Pattern:** `SongAdapter` adapts `Song` models for `RecyclerView`.

## 2. Feature Map (The "General Points")

*   **Cloud Library (GCS):**
    *   **Entry Point:** `MainActivity.fetchSongsFromBucket()`
    *   **Description:** Connects to a hardcoded GCS bucket, parses the XML listing, and populates the "Cloud Library" view.
*   **RAM Caching (Offline Mode):**
    *   **Entry Point:** `MusicService.addToPlaylist(Song)` -> `downloadAndCacheSong(Song)`
    *   **Description:** Downloads selected cloud songs into a byte array in memory (`Song.cachedData`). These persist only while the app is running.
*   **Background Playback:**
    *   **Entry Point:** `MusicService` (Service lifecycle)
    *   **Description:** Runs as a foreground service with a persistent notification to keep music playing when the app is minimized or the screen is off.
*   **Audio Playback:**
    *   **Entry Point:** `MusicService.playSong(int)`
    *   **Description:** Uses `ExoPlayer` to play media. Handles both local file URIs and cached temp files.

## 3. Complexity Hotspots (The "Complex Parts")

*   **RAM Caching & Memory Management:**
    *   **Why it's complex:** The app intentionally stores song data in RAM (`byte[]`) to avoid persistent storage requirements. This creates a high risk of `OutOfMemoryError`.
    *   **Agent Note:** Watch for the **100MB per song hard limit** in `MusicService.downloadAndCacheSong`. Ensure `Song.clearCache()` is called during cleanup to prevent leaks. The app writes RAM data to a `File.createTempFile` for ExoPlayer consumption (`MusicService.createTempFileFromCache`), which adds I/O overhead and requires careful cleanup of temp files.
*   **Custom GCS XML Parsing:**
    *   **Why it's complex:** Instead of using the heavy Google Cloud Storage SDK, the app manually fetches the public bucket URL and parses the XML response using `XmlPullParser`.
    *   **Agent Note:** This relies on the specific XML format returned by GCS public buckets. Changes to the GCS API response format will break this feature. The bucket name is hardcoded in `MainActivity`.
*   **Foreground Service & Permissions:**
    *   **Why it's complex:** Android 13/14 imposes strict requirements on foreground services and notifications (`POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`).
    *   **Agent Note:** Ensure all permissions are properly requested in `MainActivity` and declared in `AndroidManifest.xml`. Failure to do so will cause the service to crash or be killed by the OS.

## 4. Inherent Limitations & "Here be Dragons"

*   **No Persistent Database:**
    *   **Limitation:** The playlist and cached songs are session-based. Closing the app wipes everything. This is a design choice, not a bug.
*   **Hardcoded Configuration:**
    *   **Technical Debt:** The GCS bucket name (`my-sd35-space-images-2025`) is hardcoded in `MainActivity.fetchSongsFromBucket()`.
    *   **Constraint:** Do not change this unless instructed. A future refactor should move this to a configuration file or user input.
*   **Java Version Conflict:**
    *   **Known Issue:** The dev environment uses Java 21, but Gradle 8.0 may have compatibility issues ("Unsupported class file major version 65").
    *   **Workaround:** Ensure the correct JDK is selected for the Gradle daemon.
*   **Strict Memory Limits:**
    *   **Dragon:** Loading too many large FLAC files into RAM *will* crash the app. There is no global memory manager, only a per-song limit.

## 5. Dependency Graph & Key Flows

**Critical User Action: Playing a Cloud Song**

1.  **Discovery:** `MainActivity` calls `fetchSongsFromBucket` -> HTTP GET -> XML Parse -> `cloudSongs` list populated.
2.  **Selection:** User clicks song in "Cloud Library" -> `MainActivity.onSongClick` -> `MusicService.addToPlaylist`.
3.  **Caching:** `MusicService` triggers `downloadExecutor` -> Downloads bytes to `Song.cachedData` (RAM).
4.  **Playback:** User switches to "RAM Playlist" -> Clicks song -> `MusicService.playSong`.
5.  **Preparation:** `MusicService` checks `Song.isCached()` -> Writes `cachedData` to temporary file (`cacheDir`).
6.  **Rendering:** `ExoPlayer` loads temp file URI -> Audio Output.
7.  **Cleanup:** `MusicService.onDestroy` -> `clearAllCaches()` -> Deletes temp files and clears byte arrays.
