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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SongAdapter.OnSongClickListener {

    private RecyclerView recyclerView;
    private SongAdapter adapter;
    private Button btnSelectFolder;
    private Button btnPlay;
    private Button btnNext;
    private Button btnPrevious;
    private TextView tvSongTitle;
    private TextView tvSongArtist;
    private TextView tvDeviceStatus;

    private MusicService musicService;
    private boolean serviceBound = false;

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<String[]> requestMultiplePermissionsLauncher;
    private ActivityResultLauncher<Uri> openFolderLauncher;

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
        setupPermissionLaunchers();
        setupRecyclerView();
        setupClickListeners();

        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
        startService(intent);
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recyclerView);
        btnSelectFolder = findViewById(R.id.btnSelectFolder);
        btnPlay = findViewById(R.id.btnPlay);
        btnNext = findViewById(R.id.btnNext);
        btnPrevious = findViewById(R.id.btnPrevious);
        tvSongTitle = findViewById(R.id.tvSongTitle);
        tvSongArtist = findViewById(R.id.tvSongArtist);
        tvDeviceStatus = findViewById(R.id.tvDeviceStatus);
    }

    private void setupPermissionLaunchers() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        showFolderSelectionDialog();
                    } else {
                        Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show();
                    }
                });

        requestMultiplePermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }
                    if (allGranted) {
                        showFolderSelectionDialog();
                    } else {
                        Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show();
                    }
                });

        // Modern folder picker using Storage Access Framework
        openFolderLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri != null) {
                        // Persist permission for future access
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        loadSongsFromDocumentUri(uri);
                    }
                });
    }

    private void setupRecyclerView() {
        adapter = new SongAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupClickListeners() {
        btnSelectFolder.setOnClickListener(v -> checkPermissionAndSelectFolder());

        btnPlay.setOnClickListener(v -> {
            if (serviceBound && musicService != null) {
                if (musicService.isPlaying()) {
                    musicService.pause();
                } else {
                    if (musicService.getCurrentSong() != null) {
                        musicService.play();
                    } else if (adapter.getItemCount() > 0) {
                        onSongClick(adapter.getSongs().get(0), 0);
                    }
                }
            }
        });

        btnNext.setOnClickListener(v -> {
            if (serviceBound && musicService != null) {
                musicService.next();
            }
        });

        btnPrevious.setOnClickListener(v -> {
            if (serviceBound && musicService != null) {
                musicService.previous();
            }
        });
    }

    private void checkPermissionAndSelectFolder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                requestMultiplePermissionsLauncher.launch(new String[]{
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS
                });
            } else {
                showFolderSelectionDialog();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            } else {
                showFolderSelectionDialog();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDeviceStatus();
    }

    private void updateDeviceStatus() {
        // 1. Get Free RAM
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(memoryInfo);
        String availRam = Formatter.formatFileSize(this, memoryInfo.availMem);

        // 2. Get Free Storage (Internal)
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        String availStorage = Formatter.formatFileSize(this, availableBlocks * blockSize);

        // 3. Update UI
        String statusText = String.format("Free RAM: %s  |  Free Storage: %s", availRam, availStorage);

        // Change color if RAM is low (below 200MB approx)
        if (memoryInfo.availMem < 200 * 1024 * 1024) {
            tvDeviceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light));
        }

        tvDeviceStatus.setText(statusText);
    }

    private void showFolderSelectionDialog() {
        // Options for the user
        String[] options = {"Browse Device Folders", "Connect to Cloud Bucket"};

        new AlertDialog.Builder(this)
                .setTitle("Select Music Source")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // Original local folder logic
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            new AlertDialog.Builder(this)
                                    .setTitle("Select Device Folder")
                                    .setMessage("Choose how to select your music folder")
                                    .setPositiveButton("Browse Folders", (d, w) -> openFolderLauncher.launch(null))
                                    .setNeutralButton("Quick Select", (d, w) -> showLegacyFolderSelectionDialog())
                                    .show();
                        } else {
                            showLegacyFolderSelectionDialog();
                        }
                    } else {
                        // New Bucket logic
                        showBucketInput();
                    }
                })
                .show();
    }

    private void showBucketInput() {
        final EditText input = new EditText(this);
        // Pre-fill only the bucket name
        input.setText("my-sd35-space-images-2025");
        input.setHint("Bucket Name");

        new AlertDialog.Builder(this)
                .setTitle("Google Cloud Bucket")
                .setMessage("Enter public bucket name:")
                .setView(input)
                .setPositiveButton("Load Songs", (dialog, which) -> {
                    String bucketName = input.getText().toString().trim();
                    if (!bucketName.isEmpty()) {
                        // Pass the specific folder 'music/' as a second argument
                        fetchSongsFromBucket(bucketName, "music/");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void fetchSongsFromBucket(String bucketName, String folderPrefix) {
        Toast.makeText(this, "Scanning " + folderPrefix + " in " + bucketName, Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            List<Song> songs = new ArrayList<>();
            try {
                String bucketBaseUrl = "https://storage.googleapis.com/" + bucketName;

                // Construct the listing URL with the prefix filter
                // Example: https://storage.googleapis.com/my-bucket?prefix=music/
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
                            if ("Key".equals(tagName)) {
                                currentKey = parser.nextText();
                            }
                            break;
                        case XmlPullParser.END_TAG:
                            if ("Contents".equals(tagName) && currentKey != null) {
                                // currentKey will look like "music/songname.mp3"
                                if (isSupportedAudioFile(currentKey)) {
                                    // Construct download URL: base + / + key
                                    String fullUrl = bucketBaseUrl + "/" + currentKey;

                                    // Clean up the display name (remove the "music/" prefix for the UI)
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
                    if (songs.isEmpty()) {
                        Toast.makeText(this, "No music found in '" + folderPrefix + "' folder.", Toast.LENGTH_LONG).show();
                    } else {
                        adapter.setSongs(songs);
                        if (serviceBound && musicService != null) {
                            musicService.setPlaylist(songs);
                        }
                        Toast.makeText(this, "Loaded " + songs.size() + " songs from cloud", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    private void showLegacyFolderSelectionDialog() {
        File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File externalStorage = Environment.getExternalStorageDirectory();

        List<File> folders = new ArrayList<>();
        folders.add(musicDir);
        folders.add(downloadsDir);
        folders.add(externalStorage);

        String[] folderNames = new String[folders.size()];
        for (int i = 0; i < folders.size(); i++) {
            folderNames[i] = folders.get(i).getName() + " (" + folders.get(i).getAbsolutePath() + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Music Folder")
                .setItems(folderNames, (dialog, which) -> {
                    File selectedFolder = folders.get(which);
                    loadSongsFromFolder(selectedFolder);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadSongsFromFolder(File folder) {
        List<Song> songs = new ArrayList<>();
        findMusicFiles(folder, songs);

        if (songs.isEmpty()) {
            Toast.makeText(this, R.string.no_songs, Toast.LENGTH_SHORT).show();
        } else {
            adapter.setSongs(songs);
            if (serviceBound && musicService != null) {
                musicService.setPlaylist(songs);
            }
            Toast.makeText(this, "Found " + songs.size() + " songs", Toast.LENGTH_SHORT).show();
        }
    }

    private void findMusicFiles(File directory, List<Song> songs) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    findMusicFiles(file, songs);
                } else if (isSupportedAudioFile(file)) {
                    songs.add(new Song(file));
                }
            }
        }
    }

    private boolean isSupportedAudioFile(File file) {
        return isSupportedAudioFile(file.getName());
    }

    private boolean isSupportedAudioFile(String name) {
        String lowerName = name.toLowerCase();
        return lowerName.endsWith(".mp3") || lowerName.endsWith(".flac");
    }

    private void loadSongsFromDocumentUri(Uri uri) {
        List<Song> songs = new ArrayList<>();
        DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);
        if (documentFile != null) {
            findMusicFilesFromDocument(documentFile, songs);
        }

        if (songs.isEmpty()) {
            Toast.makeText(this, R.string.no_songs, Toast.LENGTH_SHORT).show();
        } else {
            adapter.setSongs(songs);
            if (serviceBound && musicService != null) {
                musicService.setPlaylist(songs);
            }
            Toast.makeText(this, "Found " + songs.size() + " songs", Toast.LENGTH_SHORT).show();
        }
    }

    private void findMusicFilesFromDocument(DocumentFile directory, List<Song> songs) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }

        DocumentFile[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (DocumentFile file : files) {
            if (file.isDirectory()) {
                findMusicFilesFromDocument(file, songs);
            } else {
                String name = file.getName();
                if (name != null && isSupportedAudioFile(name)) {
                    songs.add(new Song(file.getUri(), name));
                }
            }
        }
    }

    @Override
    public void onSongClick(Song song, int position) {
        if (serviceBound && musicService != null) {
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
        runOnUiThread(() -> {
            btnPlay.setText(isPlaying ? R.string.pause : R.string.play);
        });
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
