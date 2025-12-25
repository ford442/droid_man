# Build Guide

This document provides detailed instructions for building the DroidMan APK.

## Prerequisites

1. **Java Development Kit (JDK)**
   - JDK 8 or higher is required
   - Download from: https://adoptium.net/

2. **Android SDK**
   - Install Android Studio (recommended) or Android SDK command-line tools
   - Download from: https://developer.android.com/studio

3. **Android SDK Components Required:**
   - Android SDK Platform 34
   - Android SDK Build-Tools 34.0.0 or higher
   - Android SDK Platform-Tools

4. **Environment Variables**
   ```bash
   export ANDROID_HOME=/path/to/android/sdk
   export PATH=$PATH:$ANDROID_HOME/platform-tools
   export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
   ```

## Building with Android Studio (Recommended)

1. Open Android Studio
2. Select "Open an existing project"
3. Navigate to the cloned repository and select it
4. Wait for Gradle sync to complete
5. Build > Build Bundle(s) / APK(s) > Build APK(s)
6. The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

## Building from Command Line

### First Time Setup

1. Clone the repository:
```bash
git clone https://github.com/ford442/droid_man.git
cd droid_man
```

2. Make gradlew executable (Linux/Mac):
```bash
chmod +x gradlew
```

### Build Commands

#### Debug APK (for testing)
```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

#### Release APK (for distribution)
```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release-unsigned.apk`

Note: Release APKs need to be signed before installation.

### Signing a Release APK

1. Generate a keystore (one-time):
```bash
keytool -genkey -v -keystore my-release-key.keystore \
  -alias my-key-alias -keyalg RSA -keysize 2048 -validity 10000
```

2. Sign the APK:
```bash
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore my-release-key.keystore \
  app/build/outputs/apk/release/app-release-unsigned.apk my-key-alias
```

3. Align the APK:
```bash
zipalign -v 4 app/build/outputs/apk/release/app-release-unsigned.apk \
  app/build/outputs/apk/release/app-release.apk
```

## Installation

### Via ADB (Android Debug Bridge)

1. Enable USB debugging on your Android device
2. Connect device via USB
3. Install the APK:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Manual Installation

1. Copy the APK to your device
2. Open the APK file on your device
3. Allow installation from unknown sources if prompted
4. Follow the installation prompts

## Troubleshooting

### Gradle Build Fails

- Ensure you have internet connection for dependency downloads
- Try cleaning the project: `./gradlew clean`
- Check that ANDROID_HOME is set correctly

### SDK Not Found

- Verify Android SDK is installed
- Set ANDROID_HOME environment variable
- Install required SDK components via Android Studio SDK Manager

### Permission Denied on gradlew

```bash
chmod +x gradlew
```

### Build Tools Version Error

- Update build.gradle to use your installed build tools version
- Or install the required version via SDK Manager

## Project Structure

```
droid_man/
├── app/
│   ├── build.gradle              # App-level build configuration
│   ├── src/
│   │   └── main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/ford442/droidman/
│   │       │   ├── MainActivity.java
│   │       │   ├── MusicService.java
│   │       │   ├── Song.java
│   │       │   └── SongAdapter.java
│   │       └── res/
│   │           ├── layout/
│   │           ├── values/
│   │           ├── drawable/
│   │           └── mipmap-*/
├── build.gradle                  # Project-level build configuration
├── settings.gradle               # Project settings
└── gradle.properties             # Gradle configuration
```

## Additional Resources

- [Android Developer Documentation](https://developer.android.com/docs)
- [Gradle User Manual](https://docs.gradle.org/)
- [ExoPlayer Documentation](https://exoplayer.dev/)
