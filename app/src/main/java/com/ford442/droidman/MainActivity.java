package com.ford442.droidman;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ComponentName;
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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SongAdapter.OnSongClickListener {

    private RecyclerView recyclerView;
    private SongAdapter adapter;
    private Button btnSwitchView;
    private Button btnPlay, btnNext, btnPrevious;
    private TextView tvSongTitle, tvSongArtist, tvDeviceStatus, tvSubtitle;

    private MusicService musicService;
    private boolean serviceBound = false;
    
    // Default API URL (Replace with your actual default if desired)
    private static final String DEFAULT_API_URL = "https://test.1ink.us";
    private String currentApiUrl = DEFAULT_API_URL;

    private List<Song> cloudSongs = new ArrayList<>();
    private boolean isShowingLibrary = true;

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
            updateListView();
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
        
        // Ask for API URL on launch
        showApiUrlDialog();
        
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
    
    private void showApiUrlDialog() {
        final EditText input = new EditText(this);
        input.setText(DEFAULT_API_URL);
        input.setHint("https://your-app.hf.space");

        new AlertDialog.Builder(this)
                .setTitle("Connect to HF App API")
                .setMessage("Enter your FastAPI URL:")
                .setView(input)
                .setPositiveButton("Connect", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty()) {
                        // Ensure no trailing slash
                        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
                        currentApiUrl = url;
                        fetchSongsFromApi();
                    }
                })
                .setCancelable(false)
                .show();
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

        if (memoryInfo.availMem < 200 * 1024 * 1024) {
            tvDeviceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light));
        } else {
            tvDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.teal_200));
        }

        tvDeviceStatus.setText(statusText);
    }

    private void fetchSongsFromApi() {
        // Use 'music' as the folder name based on your previous structure
        // If your new bucket structure uses 'songs', change "music" to "songs" below
        String endpoint = currentApiUrl + "/api/storage/files?folder=music";
        
        Toast.makeText(this, "Fetching from API...", Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            List<Song> songs = new ArrayList<>();
            try {
                URL url = new URL(endpoint);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                if (connection.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    // Parse JSON Response
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    JSONArray files = jsonResponse.getJSONArray("files");

                    for (int i = 0; i < files.length(); i++) {
                        JSONObject fileObj = files.getJSONObject(i);
                        String filename = fileObj.getString("filename");

                        // Check if file is audio
                        if (isSupportedAudioFile(filename)) {
                            String fileUrl = fileObj.optString("url", null);

                            // If API didn't return a URL, assume standard GCS structure if public,
                            // or we might need a download endpoint.
                            if (fileUrl == null || fileUrl.isEmpty() || fileUrl.equals("null")) {
                                // Fallback construction if public_url is missing
                                // Warning: This assumes bucket is public or signed
                                fileUrl = "https://storage.googleapis.com/my-sd35-space-images-2025/music/" + filename;
                            }

                            songs.add(new Song(Uri.parse(fileUrl), filename));
                        }
                    }
                } else {
                    throw new Exception("HTTP Error: " + connection.getResponseCode());
                }

                runOnUiThread(() -> {
                    cloudSongs = songs;
                    if (isShowingLibrary) {
                        adapter.setSongs(cloudSongs);
                    }
                    Toast.makeText(this, "API Loaded: " + songs.size() + " songs", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "API Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private boolean isSupportedAudioFile(String name) {
        String lowerName = name.toLowerCase();
        return lowerName.endsWith(".mp3") || lowerName.endsWith(".flac") || lowerName.endsWith(".wav");
    }

    @Override
    public void onSongClick(Song song, int position) {
        if (!serviceBound || musicService == null) return;
        
        if (isShowingLibrary) {
            musicService.addToPlaylist(song);
            Toast.makeText(this, "Adding to RAM: " + song.getTitle(), Toast.LENGTH_SHORT).show();
        } else {
            musicService.playSong(position);
        }
    }

    // ... UI Update methods remain the same ...
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
