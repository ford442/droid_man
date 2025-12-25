# DroidMan Architecture

## Overview

DroidMan is an Android music player application designed to play FLAC and MP3 files offline. The app follows a clean architecture pattern with clear separation between UI, business logic, and media playback.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                        User Interface                        │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │              MainActivity                               │ │
│  │  - Song list (RecyclerView)                           │ │
│  │  - Playback controls (Play/Pause/Next/Previous)       │ │
│  │  - Now playing display                                │ │
│  │  - Folder selection                                   │ │
│  │  - Permission management                              │ │
│  └────────────────────────────────────────────────────────┘ │
│                          │                                   │
│                          │ binds to                          │
│                          ▼                                   │
│  ┌────────────────────────────────────────────────────────┐ │
│  │              MusicService                              │ │
│  │  - Background media playback                          │ │
│  │  - ExoPlayer management                               │ │
│  │  - Playlist management                                │ │
│  │  - Foreground service with notification               │ │
│  │  - Playback state management                          │ │
│  └────────────────────────────────────────────────────────┘ │
│                          │                                   │
│                          │ uses                              │
│                          ▼                                   │
│  ┌────────────────────────────────────────────────────────┐ │
│  │              ExoPlayer                                 │ │
│  │  - FLAC decoding                                      │ │
│  │  - MP3 decoding                                       │ │
│  │  - Media buffering                                    │ │
│  │  - Audio output                                       │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                        Data Layer                            │
│                                                              │
│  ┌────────────────┐    ┌────────────────┐                  │
│  │  Song.java     │    │ SongAdapter    │                  │
│  │  - File ref    │◄───│ - List display │                  │
│  │  - Metadata    │    │ - Click events │                  │
│  └────────────────┘    └────────────────┘                  │
└─────────────────────────────────────────────────────────────┘
```

## Component Details

### MainActivity

**Responsibilities:**
- Display list of songs from selected folder
- Handle user interactions (button clicks, song selection)
- Manage runtime permissions (storage, notifications)
- Bind to and communicate with MusicService
- Update UI based on playback state

**Key Features:**
- Folder selection dialog (Music, Downloads, External Storage)
- RecyclerView for efficient song list display
- Real-time playback status updates
- Permission request flow for Android 13+

### MusicService

**Responsibilities:**
- Run as a foreground service to ensure playback continues
- Manage ExoPlayer instance
- Handle play/pause/next/previous commands
- Maintain playlist state
- Display persistent notification
- Notify MainActivity of playback changes

**Key Features:**
- Lifecycle-aware (survives activity destruction)
- Automatic next song on completion
- Foreground service notification
- Clean shutdown when app is closed

### ExoPlayer

**Third-party Library:**
- Google's media player library
- Native FLAC support (not available in Android MediaPlayer)
- MP3 support
- Advanced buffering and streaming capabilities
- Robust error handling

### Song Model

**Responsibilities:**
- Represent a music file
- Store file reference and metadata
- Extract file format information

### SongAdapter

**Responsibilities:**
- Display songs in RecyclerView
- Handle song click events
- Efficient view recycling

## Data Flow

### Song Selection Flow

1. User taps "Select Music Folder"
2. App checks for storage permissions
3. If not granted, request permissions
4. Show folder selection dialog
5. Scan selected folder recursively
6. Filter for .mp3 and .flac files
7. Create Song objects
8. Update RecyclerView with song list
9. Pass playlist to MusicService

### Playback Flow

1. User taps a song in the list
2. MainActivity calls `musicService.playSong(position)`
3. MusicService:
   - Stops current playback (if any)
   - Creates MediaItem from song file URI
   - Prepares ExoPlayer
   - Starts playback
   - Updates foreground notification
   - Notifies MainActivity via callback
4. MainActivity updates "Now Playing" display
5. User sees updated play/pause button state

### App Lifecycle

**When App is Running:**
- Songs are "cached" in memory (playlist)
- MusicService keeps running in foreground
- User can listen while using other apps

**When App is Closed:**
- `onDestroy()` called in MainActivity
- Service is unbound and stopped
- ExoPlayer is released
- Foreground notification dismissed
- All resources cleaned up
- "Cache" (playlist) is cleared

## Offline Functionality

The app provides "offline listening" in the following ways:

1. **No Network Required**: All music files are read from local storage
2. **No Internet Permissions**: App doesn't require internet access
3. **Session-based Cache**: Songs are kept in memory while app is running
4. **Automatic Cleanup**: When app closes, all references are released

## Supported Audio Formats

### MP3
- Codec: MPEG-1/2 Audio Layer III
- Support: Native Android MediaCodec + ExoPlayer
- File Extension: .mp3

### FLAC
- Codec: Free Lossless Audio Codec
- Support: ExoPlayer (not in standard Android MediaPlayer)
- File Extension: .flac
- Benefits: Lossless compression, better quality

## Permissions

### Required Permissions

1. **READ_EXTERNAL_STORAGE** (Android 12 and below)
   - Access music files in device storage
   - Requested at runtime

2. **READ_MEDIA_AUDIO** (Android 13+)
   - Granular permission for audio files only
   - Requested at runtime

3. **WAKE_LOCK**
   - Keep CPU awake during playback
   - Prevents audio interruptions
   - Declared in manifest (not dangerous)

4. **FOREGROUND_SERVICE**
   - Run MusicService in foreground
   - Declared in manifest (not dangerous)

5. **POST_NOTIFICATIONS** (Android 13+)
   - Show playback notification
   - Requested at runtime

## Technical Decisions

### Why ExoPlayer?

- **FLAC Support**: Android's MediaPlayer doesn't support FLAC
- **Better Performance**: More efficient buffering
- **Active Development**: Well-maintained by Google
- **Flexibility**: Easy to extend for future formats

### Why Foreground Service?

- **Background Playback**: Continue playing when app is in background
- **Android Requirements**: Required for media playback services
- **User Awareness**: Notification keeps user informed
- **Battery Optimization**: Prevents system from killing the service

### Why RecyclerView?

- **Performance**: Efficient view recycling for large lists
- **Smooth Scrolling**: Better than ListView
- **Modern Standard**: Current Android best practice

### Why No Database?

- **Simplicity**: Minimal scope per requirements
- **Session-based**: No need to persist playlist
- **File System is Source**: Real-time scanning ensures up-to-date list

## Future Enhancements

Potential improvements (not in current scope):

- Persistent playlist storage
- Shuffle and repeat modes
- Equalizer integration
- Album art display
- Search and filter functionality
- Multiple playlist support
- OGG Vorbis support
- Sleep timer
- Headphone controls
- Widget support
