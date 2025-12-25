package com.ford442.droidman;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SongAdapter.OnSongClickListener {

    private RecyclerView recyclerView;
    private SongAdapter adapter;
    private Button btnSelectFolder;
    private Button btnPlay;
    private Button btnNext;
    private Button btnPrevious;
    private TextView tvSongTitle;
    private TextView tvSongArtist;

    private MusicService musicService;
    private boolean serviceBound = false;

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<String[]> requestMultiplePermissionsLauncher;

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

    private void showFolderSelectionDialog() {
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
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp3") || name.endsWith(".flac");
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
