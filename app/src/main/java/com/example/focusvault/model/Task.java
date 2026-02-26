package com.example.focusvault.model;

public class Task {
    private final int id;
    private final String title;
    private final int isDone;
    private final String date;
    private final int priority;

    public Task(int id, String title, int isDone, String date, int priority) {
        this.id = id;
        this.title = title;
        this.isDone = isDone;
        this.date = date;
        this.priority = priority;
    }

    public int getId() { return id; }

    public String getTitle() { return title; }

    public int getIsDone() { return isDone; }

    public String getDate() { return date; }

    public int getPriority() { return priority; }
}
