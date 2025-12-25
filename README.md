# DroidMan

Like a walkman for smartphone. This APK app saves FLAC/MP3 songs for offline listening until closed.

## Features

- **Offline Music Playback**: Browse and play FLAC and MP3 files from your device
- **Background Playback**: Continue listening while using other apps
- **Simple Controls**: Play, pause, next, previous controls
- **Format Support**: Native support for MP3 and FLAC audio formats using ExoPlayer
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
3. Tap "Select Music Folder" to choose a folder containing your music files
4. Browse and tap on songs to play them
5. Use the playback controls to control playback
6. The app will cache songs for offline listening while it's running
7. When you close the app, the cache is automatically cleared

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
