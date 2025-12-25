package com.ford442.droidman;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicService extends Service {
    
    private static final String TAG = "MusicService";
    private static final String CHANNEL_ID = "MusicPlaybackChannel";
    private static final int NOTIFICATION_ID = 1;
    
    // Action constants for notification controls
    public static final String ACTION_PLAY = "com.ford442.droidman.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.ford442.droidman.ACTION_PAUSE";
    public static final String ACTION_NEXT = "com.ford442.droidman.ACTION_NEXT";
    public static final String ACTION_PREVIOUS = "com.ford442.droidman.ACTION_PREVIOUS";

    private ExoPlayer player;
    private final IBinder binder = new MusicBinder();
    private List<Song> playlist = new ArrayList<>();
    private int currentPosition = -1;
    private PlaybackListener playbackListener;
    private NotificationActionReceiver notificationActionReceiver;
    private ExecutorService downloadExecutor;
    private Map<String, File> tempFileCache = new HashMap<>();

    public interface PlaybackListener {
        void onSongChanged(Song song, int position);
        void onPlaybackStateChanged(boolean isPlaying);
    }

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initializePlayer();
        registerNotificationReceiver();
        downloadExecutor = Executors.newSingleThreadExecutor();
    }
    
    private void registerNotificationReceiver() {
        notificationActionReceiver = new NotificationActionReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PLAY);
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_PREVIOUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(notificationActionReceiver, filter);
        }
    }
    
    private class NotificationActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            
            switch (action) {
                case ACTION_PLAY:
                    play();
                    break;
                case ACTION_PAUSE:
                    pause();
                    break;
                case ACTION_NEXT:
                    next();
                    break;
                case ACTION_PREVIOUS:
                    previous();
                    break;
            }
        }
    }

    private void initializePlayer() {
        player = new ExoPlayer.Builder(this).build();
        
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    next();
                }
                if (playbackListener != null) {
                    playbackListener.onPlaybackStateChanged(player.isPlaying());
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (playbackListener != null) {
                    playbackListener.onPlaybackStateChanged(isPlaying);
                }
                updateNotification();
            }
        });
    }

    public void setPlaybackListener(PlaybackListener listener) {
        this.playbackListener = listener;
    }

    public void setPlaylist(List<Song> songs) {
        this.playlist = new ArrayList<>(songs);
        // Start caching songs in the background
        downloadExecutor.execute(() -> cacheAllSongs());
    }

    private void cacheAllSongs() {
        for (Song song : playlist) {
            if (song.isUriBased() && !song.isCached()) {
                try {
                    downloadAndCacheSong(song);
                } catch (Exception e) {
                    Log.e(TAG, "Error caching song: " + song.getTitle(), e);
                }
            }
        }
        Log.i(TAG, "All songs cached for offline playback");
    }

    private void downloadAndCacheSong(Song song) throws Exception {
        Uri uri = song.getUri();
        String uriString = uri.toString();
        
        // Check if it's a network URL
        if (!uriString.startsWith("http://") && !uriString.startsWith("https://")) {
            // Local file, no need to cache
            return;
        }
        
        Log.i(TAG, "Downloading song to RAM: " + song.getTitle());
        
        URL url = new URL(uriString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        
        InputStream inputStream = connection.getInputStream();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        
        inputStream.close();
        connection.disconnect();
        
        byte[] songData = outputStream.toByteArray();
        song.setCachedData(songData);
        
        Log.i(TAG, "Cached " + songData.length + " bytes for: " + song.getTitle());
    }

    public void playSong(int position) {
        if (position < 0 || position >= playlist.size()) {
            return;
        }

        currentPosition = position;
        Song song = playlist.get(position);
        
        MediaItem mediaItem;
        
        // Check if song is cached in RAM
        if (song.isCached()) {
            // Use cached data
            try {
                File tempFile = createTempFileFromCache(song);
                mediaItem = MediaItem.fromUri(Uri.fromFile(tempFile));
                Log.i(TAG, "Playing from cache: " + song.getTitle());
            } catch (Exception e) {
                Log.e(TAG, "Error creating temp file from cache", e);
                // Fallback to streaming
                if (song.isUriBased()) {
                    mediaItem = MediaItem.fromUri(song.getUri());
                } else {
                    mediaItem = MediaItem.fromUri(Uri.fromFile(song.getFile()));
                }
            }
        } else if (song.isUriBased()) {
            // If not cached yet, download and cache in background, but stream for now
            if (song.getUri().toString().startsWith("http")) {
                downloadExecutor.execute(() -> {
                    try {
                        downloadAndCacheSong(song);
                    } catch (Exception e) {
                        Log.e(TAG, "Error caching song on play", e);
                    }
                });
            }
            mediaItem = MediaItem.fromUri(song.getUri());
        } else {
            mediaItem = MediaItem.fromUri(Uri.fromFile(song.getFile()));
        }
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();

        if (playbackListener != null) {
            playbackListener.onSongChanged(song, position);
        }

        startForeground(NOTIFICATION_ID, createNotification());
    }

    public void play() {
        if (player != null) {
            player.play();
        }
    }

    public void pause() {
        if (player != null) {
            player.pause();
        }
    }

    public void stop() {
        if (player != null) {
            player.stop();
        }
        currentPosition = -1;
    }

    public void next() {
        if (currentPosition < playlist.size() - 1) {
            playSong(currentPosition + 1);
        }
    }

    public void previous() {
        if (currentPosition > 0) {
            playSong(currentPosition - 1);
        }
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public Song getCurrentSong() {
        if (currentPosition >= 0 && currentPosition < playlist.size()) {
            return playlist.get(currentPosition);
        }
        return null;
    }

    private File createTempFileFromCache(Song song) throws Exception {
        String key = song.getPath();
        
        // Check if we already have a temp file for this song
        if (tempFileCache.containsKey(key)) {
            File existingFile = tempFileCache.get(key);
            if (existingFile != null && existingFile.exists()) {
                return existingFile;
            }
        }
        
        // Create a new temp file
        String extension = song.getFormat().toLowerCase();
        File tempFile = File.createTempFile("droidman_", "." + extension, getCacheDir());
        
        // Write cached data to temp file
        FileOutputStream fos = new FileOutputStream(tempFile);
        fos.write(song.getCachedData());
        fos.close();
        
        // Store in temp file cache
        tempFileCache.put(key, tempFile);
        
        return tempFile;
    }

    private void clearAllCaches() {
        // Clear RAM cache from songs
        for (Song song : playlist) {
            song.clearCache();
        }
        
        // Delete temp files
        for (File tempFile : tempFileCache.values()) {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
        tempFileCache.clear();
        
        Log.i(TAG, "All caches cleared");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Controls for music playback");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        String contentText = "No song playing";
        if (currentPosition >= 0 && currentPosition < playlist.size()) {
            Song song = playlist.get(currentPosition);
            contentText = song.getTitle();
        }

        // Create an Intent to open the app when notification is clicked
        Intent contentIntent = new Intent(this, MainActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Create PendingIntents for playback control actions
        PendingIntent previousIntent = PendingIntent.getBroadcast(
                this, 1, new Intent(ACTION_PREVIOUS), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent nextIntent = PendingIntent.getBroadcast(
                this, 2, new Intent(ACTION_NEXT), PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DroidMan")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(contentPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setStyle(new MediaStyle().setShowActionsInCompactView(0, 1, 2));

        // Add Previous action
        builder.addAction(android.R.drawable.ic_media_previous, "Previous", previousIntent);
        
        // Add Play/Pause action based on current state
        if (player != null && player.isPlaying()) {
            PendingIntent pauseIntent = PendingIntent.getBroadcast(
                    this, 3, new Intent(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent);
        } else {
            PendingIntent playIntent = PendingIntent.getBroadcast(
                    this, 4, new Intent(ACTION_PLAY), PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(android.R.drawable.ic_media_play, "Play", playIntent);
        }
        
        // Add Next action
        builder.addAction(android.R.drawable.ic_media_next, "Next", nextIntent);

        return builder.build();
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification());
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (notificationActionReceiver != null) {
            unregisterReceiver(notificationActionReceiver);
            notificationActionReceiver = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        // Clear all cached data
        clearAllCaches();
        // Shutdown download executor
        if (downloadExecutor != null) {
            downloadExecutor.shutdown();
        }
        stopForeground(true);
    }
}
