package com.ford442.droidman;

import android.net.Uri;

import java.io.File;

public class Song {
    private File file;
    private Uri uri;
    private String title;
    private String artist;
    private String format;
    private String path;
    private byte[] cachedData; // Store song data in RAM for offline playback

    // Constructor for File-based songs (legacy)
    public Song(File file) {
        this.file = file;
        this.uri = null;
        this.title = file.getName();
        this.format = getFileExtension(file.getName());
        this.artist = "Unknown Artist";
        this.path = file.getAbsolutePath();
    }

    // Constructor for Uri-based songs (modern SAF)
    public Song(Uri uri, String displayName) {
        this.file = null;
        this.uri = uri;
        this.title = displayName;
        this.format = getFileExtension(displayName);
        this.artist = "Unknown Artist";
        this.path = uri.toString();
    }

    public File getFile() {
        return file;
    }

    public Uri getUri() {
        return uri;
    }

    public boolean isUriBased() {
        return uri != null;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getFormat() {
        return format;
    }

    public String getPath() {
        return path;
    }

    private String getFileExtension(String name) {
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            return name.substring(lastDot + 1).toUpperCase();
        }
        return "";
    }

    public byte[] getCachedData() {
        return cachedData;
    }

    public void setCachedData(byte[] cachedData) {
        this.cachedData = cachedData;
    }

    public boolean isCached() {
        return cachedData != null;
    }

    public void clearCache() {
        cachedData = null;
    }
}
