package com.djactor.models;

import java.time.Instant;

/**
 * Represents the state of a media player
 */
public class PlayerState {
    private PlayerStatus status; // status of the player - enum
    private int currentTrackId;
    private Instant startedAt; // timestamp when started playing
    private long positionMs; // current position in milliseconds in the current song

    public PlayerState(PlayerStatus status, int currentTrackId, Instant startedAt, long positionMs) {
        this.status = status;
        this.currentTrackId = currentTrackId;
        this.startedAt = startedAt;
        this.positionMs = positionMs;
    }

    public void changeSong(boolean isNextSong) {
        this.positionMs = 0;
        if (isNextSong) {
            this.status = PlayerStatus.PLAYING;
        } else {
            this.status = PlayerStatus.STOPPED;
        }
    }

    // getters/setters
    public PlayerStatus getStatus() {
        return status;
    }

    public void setStatus(PlayerStatus status) {
        this.status = status;
    }

    public int getCurrentTrackId() {
        return currentTrackId;
    }

    /**
     * @throws IllegalArgumentException if currentTrackId is negative
     */
    public void setCurrentTrackId(int currentTrackId) {
        if (currentTrackId < 0) {
            throw new IllegalArgumentException("Track ID cannot be negative");
        }
        this.currentTrackId = currentTrackId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public long getPositionMs() {
        return positionMs;
    }

    /**
     * sets the position in milliseconds, zero if negative
     * 
     * @param positionMs
     */
    public void setPositionMs(long positionMs) {
        if (positionMs < 0) {
            this.positionMs = 0;
        } else {
            this.positionMs = positionMs;
        }
    }
}
