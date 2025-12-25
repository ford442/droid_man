package com.ford442.droidman;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

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

    private ExoPlayer player;
    private final IBinder binder = new MusicBinder();
    private List<Song> playlist = new ArrayList<>();
    private int currentPosition = -1;
    private PlaybackListener playbackListener;

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
        
        MediaItem mediaItem = MediaItem.fromUri(Uri.fromFile(song.getFile()));
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

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DroidMan")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
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
        if (player != null) {
            player.release();
            player = null;
        }
        stopForeground(true);
    }
}
