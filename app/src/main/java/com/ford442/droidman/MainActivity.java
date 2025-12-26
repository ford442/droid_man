package com.ford442.droidman;

import android.Manifest;
import android.content.ComponentName;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.StatFs;
import android.text.format.Formatter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SongAdapter.OnSongClickListener {

    private RecyclerView recyclerView;
    private SongAdapter adapter;
    private Button btnSwitchView; // Renamed from btnSelectFolder
    private Button btnPlay, btnNext, btnPrevious;
    private TextView tvSongTitle, tvSongArtist, tvDeviceStatus, tvSubtitle;

    private MusicService musicService;
    private boolean serviceBound = false;
    
    // Two lists: One for what's in the cloud, one for what's in RAM
    private List<Song> cloudSongs = new ArrayList<>();
    private boolean isShowingLibrary = true; // Track which view we are in

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            serviceBound = true;

            musicService.setPlaybackListener(new MusicService.PlaybackListener() {
                @Override
                public void onSongChanged(Song song, int position) {
                    updateNowPlaying(song);
                }
                @Override
                public void onPlaybackStateChanged(boolean isPlaying) {
                    updatePlayButton(isPlaying);
                }
            });
            // Don't set playlist automatically, we build it manually now
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            musicService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupRecyclerView();
        setupClickListeners();
        
        // Auto-connect to your bucket immediately
        fetchSongsFromBucket();
        
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
        startService(intent);
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recyclerView);
        btnSwitchView = findViewById(R.id.btnSwitchView);
        btnPlay = findViewById(R.id.btnPlay);
        btnNext = findViewById(R.id.btnNext);
        btnPrevious = findViewById(R.id.btnPrevious);
        tvSongTitle = findViewById(R.id.tvSongTitle);
        tvSongArtist = findViewById(R.id.tvSongArtist);
        tvDeviceStatus = findViewById(R.id.tvDeviceStatus);
        tvSubtitle = findViewById(R.id.tvSubtitle);
    }

    private void setupRecyclerView() {
        adapter = new SongAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupClickListeners() {
        // Toggle between Cloud Library and RAM Playlist
        btnSwitchView.setOnClickListener(v -> {
            isShowingLibrary = !isShowingLibrary;
            updateListView();
        });

        btnPlay.setOnClickListener(v -> {
            if (serviceBound && musicService != null) {
                if (musicService.isPlaying()) musicService.pause();
                else musicService.play();
            }
        });

        btnNext.setOnClickListener(v -> { if (serviceBound && musicService != null) musicService.next(); });
        btnPrevious.setOnClickListener(v -> { if (serviceBound && musicService != null) musicService.previous(); });
    }
    
    private void updateListView() {
        if (isShowingLibrary) {
            btnSwitchView.setText("View: Cloud Library");
            tvSubtitle.setText("Tap cloud songs to add to RAM");
            adapter.setSongs(cloudSongs);
        } else {
            btnSwitchView.setText("View: RAM Playlist");
            tvSubtitle.setText("Songs currently loaded in RAM");
            if (serviceBound && musicService != null) {
                adapter.setSongs(musicService.getPlaylist());
            } else {
                adapter.setSongs(new ArrayList<>());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDeviceStatus();
    }

    private void updateDeviceStatus() {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(memoryInfo);
        String availRam = Formatter.formatFileSize(this, memoryInfo.availMem);

        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        String availStorage = Formatter.formatFileSize(this, availableBlocks * blockSize);

        String statusText = String.format("Free RAM: %s  |  Free Storage: %s", availRam, availStorage);

        // Safe color check
        if (memoryInfo.availMem < 200 * 1024 * 1024) {
            tvDeviceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light));
        } else {
            tvDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.teal_200));
        }

        tvDeviceStatus.setText(statusText);
    }

    // Hardcoded fetch for your specific bucket
    private void fetchSongsFromBucket() {
        String bucketName = "my-sd35-space-images-2025";
        String folderPrefix = "music/";
        
        Toast.makeText(this, "Connecting to Cloud Library...", Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            List<Song> songs = new ArrayList<>();
            try {
                String bucketBaseUrl = "https://storage.googleapis.com/" + bucketName;
                URL listUrl = new URL(bucketBaseUrl + "?prefix=" + folderPrefix);

                HttpURLConnection connection = (HttpURLConnection) listUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                InputStream inputStream = connection.getInputStream();
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                XmlPullParser parser = factory.newPullParser();
                parser.setInput(inputStream, null);

                int eventType = parser.getEventType();
                String currentKey = null;

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    String tagName = parser.getName();
                    switch (eventType) {
                        case XmlPullParser.START_TAG:
                            if ("Key".equals(tagName)) currentKey = parser.nextText();
                            break;
                        case XmlPullParser.END_TAG:
                            if ("Contents".equals(tagName) && currentKey != null) {
                                if (isSupportedAudioFile(currentKey)) {
                                    String fullUrl = bucketBaseUrl + "/" + currentKey;
                                    String displayName = currentKey;
                                    if (displayName.startsWith(folderPrefix)) {
                                        displayName = displayName.substring(folderPrefix.length());
                                    }
                                    songs.add(new Song(Uri.parse(fullUrl), displayName));
                                }
                                currentKey = null;
                            }
                            break;
                    }
                    eventType = parser.next();
                }

                runOnUiThread(() -> {
                    cloudSongs = songs;
                    if (isShowingLibrary) {
                        adapter.setSongs(cloudSongs);
                    }
                    Toast.makeText(this, "Library loaded: " + songs.size() + " songs", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private boolean isSupportedAudioFile(String name) {
        String lowerName = name.toLowerCase();
        return lowerName.endsWith(".mp3") || lowerName.endsWith(".flac");
    }

    // New logic: Click behavior depends on the view mode
    @Override
    public void onSongClick(Song song, int position) {
        if (!serviceBound || musicService == null) return;
        
        if (isShowingLibrary) {
            // LIBRARY MODE: Add to Playlist (RAM)
            musicService.addToPlaylist(song);
            Toast.makeText(this, "Downloading to RAM: " + song.getTitle(), Toast.LENGTH_SHORT).show();
            // Optional: Switch to playlist view automatically? 
            // For now, let's just stay in library so user can add more.
        } else {
            // PLAYLIST MODE: Play the song
            musicService.playSong(position);
        }
    }

    private void updateNowPlaying(Song song) {
        runOnUiThread(() -> {
            if (song != null) {
                tvSongTitle.setText(song.getTitle());
                tvSongArtist.setText(song.getArtist());
            } else {
                tvSongTitle.setText("No song playing");
                tvSongArtist.setText("");
            }
        });
    }

    private void updatePlayButton(boolean isPlaying) {
        runOnUiThread(() -> btnPlay.setText(isPlaying ? R.string.pause : R.string.play));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        Intent intent = new Intent(this, MusicService.class);
        stopService(intent);
    }
}
