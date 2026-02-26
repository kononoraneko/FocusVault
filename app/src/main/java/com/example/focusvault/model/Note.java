package com.example.focusvault.model;

public class Note {
    private final int id;
    private final String title;
    private final String content;
    private final String createdAt;
    private final int isFavorite;

    public Note(int id, String title, String content, String createdAt, int isFavorite) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
        this.isFavorite = isFavorite;
    }

    public int getId() { return id; }

    public String getTitle() { return title; }

    public String getContent() { return content; }

    public String getCreatedAt() { return createdAt; }

    public int getIsFavorite() { return isFavorite; }
}
