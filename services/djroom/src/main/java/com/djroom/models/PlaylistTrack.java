package com.djroom.models;

import java.io.Serializable;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

/**
 * Represents a track in the playlist
 */
public class PlaylistTrack implements Serializable {

    private final long id;
    private final String url;
    private final String title;
    private final long durationMs;
    private int score;
    private final Instant addedAt;
    private final String addedBy;

    public PlaylistTrack(long id, String url, String title, long durationMs,
                         int score, Instant addedAt, String addedBy) {
        this.id = id;
        this.url = Objects.requireNonNull(url, "URL cannot be null");
        this.title = Objects.requireNonNull(title, "Title cannot be null");
        this.durationMs = durationMs;
        this.score = Math.max(0, score);
        this.addedAt = Objects.requireNonNull(addedAt, "AddedAt cannot be null");
        this.addedBy = Objects.requireNonNull(addedBy, "AddedBy cannot be null");
    }

    // Getters
    public long getId() { return id; }
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public long getDurationMs() { return durationMs; }
    public int getScore() { return score; }
    public Instant getAddedAt() { return addedAt; }
    public String getAddedBy() { return addedBy; }


    public void addScore(int delta) {
        this.score = Math.max(0, this.score + delta);
    }

    public static final Comparator<PlaylistTrack> BY_SCORE_DESC_THEN_FIFO =
            Comparator.comparingInt(PlaylistTrack::getScore).reversed()
                    .thenComparing(PlaylistTrack::getAddedAt);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlaylistTrack)) return false;
        PlaylistTrack that = (PlaylistTrack) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PlaylistTrack{id=" + id + ", title='" + title + "', score=" + score + "}";
    }
}