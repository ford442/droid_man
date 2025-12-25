package com.ford442.droidman;

import java.io.File;

public class Song {
    private File file;
    private String title;
    private String artist;
    private String format;

    public Song(File file) {
        this.file = file;
        this.title = file.getName();
        this.format = getFileExtension(file);
        this.artist = "Unknown Artist";
    }

    public File getFile() {
        return file;
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
        return file.getAbsolutePath();
    }

    private String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            return name.substring(lastDot + 1).toUpperCase();
        }
        return "";
    }
}
