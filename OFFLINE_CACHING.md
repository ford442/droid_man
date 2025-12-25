# Offline Song Caching Implementation

## Overview

This document describes the implementation of offline song caching functionality for the DroidMan app. The feature allows songs loaded from network sources (e.g., Google Cloud Storage) to be downloaded and cached in RAM for offline playback.

## Problem Statement

When the app loads a song from a cloud URL, it needs to:
1. Add the file to the playlist
2. Download and store the song data in RAM
3. Allow offline playback (without WiFi or 3G)
4. Clear the cache when the app is closed

## Solution

### Architecture

The caching system works as follows:

1. **Song Model Enhancement** (`Song.java`)
   - Added `cachedData` field to store song bytes in RAM
   - Added methods: `getCachedData()`, `setCachedData()`, `isCached()`, `clearCache()`

2. **Background Download** (`MusicService.java`)
   - When a playlist is set, automatically downloads all network songs in the background
   - Uses a single-threaded executor for sequential downloads
   - Downloads songs to byte arrays stored in memory

3. **Playback Priority**
   - If a song is cached: Use cached data (offline playback)
   - If a song is not cached: Stream from URL and cache in background
   - If caching fails: Fall back to streaming

4. **Cleanup**
   - When the service is destroyed (app closed), all cached data is cleared
   - Temporary files are deleted
   - RAM is freed

### Key Components

#### Song.java
```java
private byte[] cachedData; // Store song data in RAM

public boolean isCached() {
    return cachedData != null;
}

public void clearCache() {
    cachedData = null;
}
```

#### MusicService.java

**Background Caching:**
```java
public void setPlaylist(List<Song> songs) {
    this.playlist = new ArrayList<>(songs);
    downloadExecutor.execute(() -> cacheAllSongs());
}

private void cacheAllSongs() {
    for (Song song : playlist) {
        if (song.isUriBased() && !song.isCached()) {
            downloadAndCacheSong(song);
        }
    }
}
```

**Download Implementation:**
```java
private void downloadAndCacheSong(Song song) throws Exception {
    Uri uri = song.getUri();
    String uriString = uri.toString();
    
    if (!uriString.startsWith("http://") && !uriString.startsWith("https://")) {
        return; // Local file, no need to cache
    }
    
    URL url = new URL(uriString);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    InputStream inputStream = connection.getInputStream();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    
    byte[] buffer = new byte[8192];
    int bytesRead;
    while ((bytesRead = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, bytesRead);
    }
    
    byte[] songData = outputStream.toByteArray();
    song.setCachedData(songData);
}
```

**Playback with Caching:**
```java
public void playSong(int position) {
    Song song = playlist.get(position);
    MediaItem mediaItem;
    
    if (song.isCached()) {
        // Use cached data - offline playback
        File tempFile = createTempFileFromCache(song);
        mediaItem = MediaItem.fromUri(Uri.fromFile(tempFile));
    } else if (song.isUriBased()) {
        // Stream and cache in background
        downloadExecutor.execute(() -> downloadAndCacheSong(song));
        mediaItem = MediaItem.fromUri(song.getUri());
    } else {
        // Local file
        mediaItem = MediaItem.fromUri(Uri.fromFile(song.getFile()));
    }
    
    player.setMediaItem(mediaItem);
    player.prepare();
    player.play();
}
```

**Cleanup:**
```java
@Override
public void onDestroy() {
    clearAllCaches();
    if (downloadExecutor != null) {
        downloadExecutor.shutdown();
    }
    stopForeground(true);
}

private void clearAllCaches() {
    for (Song song : playlist) {
        song.clearCache();
    }
    for (File tempFile : tempFileCache.values()) {
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
    }
    tempFileCache.clear();
}
```

## Behavior

### For Local Files
- No caching needed (already offline)
- Plays directly from file system
- No network required

### For Cloud URLs
1. **First Load:**
   - Songs added to playlist
   - Background download starts immediately
   - First playback may stream while downloading
   
2. **Subsequent Playback:**
   - Plays from RAM cache
   - No network required
   - Instant playback

3. **App Close:**
   - All cached data cleared from RAM
   - Temporary files deleted
   - Next launch starts fresh

## Benefits

1. **Offline Playback:** Once cached, songs play without internet
2. **Background Caching:** Downloads happen automatically without blocking UI
3. **Memory Efficient:** Only active playlist is cached
4. **Automatic Cleanup:** Cache cleared when app closes
5. **Fallback Support:** Falls back to streaming if caching fails

## Memory Considerations

- Each song is stored as a byte array in RAM
- A typical 5MB MP3 song = 5MB RAM usage
- 10 songs playlist ≈ 50MB RAM
- Large FLAC files may use 20-50MB each
- Cache is cleared when app closes to free memory

## Testing

To test the caching functionality:

1. **Local Files:**
   - Select a local folder with MP3/FLAC files
   - Should play normally (no caching needed)

2. **Cloud Files:**
   - Connect to a cloud bucket
   - Load songs from cloud
   - Check logcat for "Downloading song to RAM" messages
   - Wait for "All songs cached for offline playback" message
   - Disable WiFi/data
   - Songs should still play from cache

3. **App Lifecycle:**
   - Close the app
   - Check logcat for "All caches cleared" message
   - Reopen app
   - Cache should be empty, ready for new downloads

## Logs

The implementation logs key events:

```
I/MusicService: Downloading song to RAM: song1.mp3
I/MusicService: Cached 5242880 bytes for: song1.mp3
I/MusicService: All songs cached for offline playback
I/MusicService: Playing from cache: song1.mp3
I/MusicService: All caches cleared
```

## Future Enhancements

Possible improvements (not implemented):

- Persistent cache (save to disk between sessions)
- Cache size limits
- Selective caching (user chooses which songs)
- Cache progress indicator in UI
- Pre-caching based on playback history
- Cache compression
