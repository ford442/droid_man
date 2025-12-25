# DroidMan

Like a walkman for smartphone. This APK app saves FLAC/MP3 songs for offline listening until closed.

## Features

- **Offline Music Playback**: Browse and play FLAC and MP3 files from your device
- **Automatic Caching**: Songs from cloud sources are downloaded and cached in RAM for offline playback
- **Background Playback**: Continue listening while using other apps
- **Simple Controls**: Play, pause, next, previous controls
- **Format Support**: Native support for MP3 and FLAC audio formats using ExoPlayer
- **Cloud Support**: Load songs from Google Cloud Storage buckets
- **Automatic Cleanup**: Music cache is cleared when the app is closed

## Requirements

- Android 7.0 (API 24) or higher
- Storage permission to access music files

## Building the APK

### Prerequisites
- Java Development Kit (JDK) 8 or higher
- Android SDK
- Gradle

### Build Steps

1. Clone the repository:
```bash
git clone https://github.com/ford442/droid_man.git
cd droid_man
```

2. Build the APK:
```bash
./gradlew assembleDebug
```

3. The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install on Device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or transfer the APK to your device and install manually.

## Usage

1. Launch the app
2. Grant storage permissions when prompted
3. Tap "Select Music Folder" to choose a music source:
   - **Browse Device Folders**: Select local folders with MP3/FLAC files
   - **Connect to Cloud Bucket**: Load songs from Google Cloud Storage
4. Browse and tap on songs to play them
5. Use the playback controls to control playback
6. **Automatic Caching**: Songs from cloud sources are automatically downloaded and cached in RAM for offline playback
7. **Offline Mode**: Once cached, songs can be played without WiFi or mobile data
8. When you close the app, the cache is automatically cleared

## How Caching Works

- When you load songs from a cloud source, they are automatically downloaded in the background
- Downloaded songs are stored in RAM (up to 100MB per song)
- Once cached, songs play instantly without requiring internet connection
- Local files (from device storage) don't need caching as they're already offline
- Cache is cleared when the app is closed to free up memory

For technical details, see [OFFLINE_CACHING.md](OFFLINE_CACHING.md)

## Supported Formats

- MP3 (.mp3)
- FLAC (.flac)

## Architecture

- **MainActivity**: Main UI with song list and playback controls
- **MusicService**: Background service for media playback using ExoPlayer
- **Song**: Data model for audio files
- **SongAdapter**: RecyclerView adapter for displaying the song list

## License

Open source - feel free to use and modify.
