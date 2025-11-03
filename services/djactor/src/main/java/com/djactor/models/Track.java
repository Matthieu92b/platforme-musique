package com.djactor.models;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

public final class Track {
    private final long id;
    private final String title;
    private final String url;
    private int score;
    private long durationMs;
    private final Instant addedAt;

    public Track(long id, String title, String url, int score, long durationMs, Instant addedAt) {
        if (id < 0)
            throw new IllegalArgumentException("id must be >= 0");
        this.id = id;
        this.title = Objects.requireNonNull(title);
        this.url = Objects.requireNonNull(url);
        this.score = Math.max(0, score);
        this.durationMs = durationMs;
        this.addedAt = Objects.requireNonNull(addedAt);
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public int getScore() {
        return score;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public void addScore(int delta) {
        this.score = Math.max(0, this.score + delta);
    }

    public static final Comparator<Track> BY_SCORE_DESC_THEN_FIFO = Comparator.comparingInt(Track::getScore).reversed()
            .thenComparing(Track::getAddedAt);

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Track))
            return false;
        Track track = (Track) o;
        return id == track.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Track{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", score=" + score +
                ", addedAt=" + addedAt +
                '}';
    }
}
