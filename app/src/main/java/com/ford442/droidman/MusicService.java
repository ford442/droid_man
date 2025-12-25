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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
    }

    public void playSong(int position) {
        if (position < 0 || position >= playlist.size()) {
            return;
        }

        currentPosition = position;
        Song song = playlist.get(position);
        
        MediaItem mediaItem;
        if (song.isUriBased()) {
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
        stopForeground(true);
    }
}
