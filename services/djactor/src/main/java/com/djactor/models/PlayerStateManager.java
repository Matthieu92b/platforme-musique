package com.djactor.models;

import java.time.Instant;

/**
 * Represents the state of a media player and contains data about the current
 * song being played. also manages skip/prev/pause actions
 * knows the playlist it's currently playing
 */
public class PlayerStateManager {
    private PlayerStatus status; // status of the player - enum
    private int currentTrackID;
    private Instant startedAt; // timestamp when started playing the current song
    private long positionMs; // current position in milliseconds in the current song
    // TODO : playlist/music variables to add

    // constructor that sets the state manager to a default status
    public PlayerStateManager() {
        setIdle();
    }

    // TODO : constructor that sets the state manager with a default playlist
    // public PlayerStateManager(playlist) {
    // set playlist
    // startNewSong(first song in playlist);
    // }

    /**
     * performs actions to move to the next song in the playlist
     */
    public void nextSong(boolean test) {
        if (test) // if next song in play list or no playlist
            startNewSong(0); // TODO : add next song in playlist
        else {
            setIdle();
        }
    }

    /**
     * performs actions to move to the prev song
     */
    public void prevSong(boolean test) {
        if (test && getPositionMs() < 3000) // if no prev song in play list and if at the beginning of the song (<3s)
            setPositionMs(0);
        else if (test) {
            startNewSong(0); // TODO : add prev playlist
        } else {
            setIdle();
        }
    }

    /**
     * Toggles between PLAYING and PAUSED states
     * if STOPPED, does nothing
     */
    public void togglePlayPause() {
        if (this.status == PlayerStatus.PLAYING)
            this.status = PlayerStatus.PAUSED;
        else if (this.status == PlayerStatus.PAUSED)
            this.status = PlayerStatus.PLAYING;
    }

    /**
     * Starts playing a new song from the beginning
     * 
     * @param trackID ID of the track to play
     */
    public void startNewSong(int trackID) {
        setCurrentTrackID(trackID);
        this.status = PlayerStatus.PLAYING;
        this.startedAt = Instant.now();
        this.positionMs = 0;
    }

    public void setIdle() {
        this.status = PlayerStatus.STOPPED;
        this.currentTrackID = 0;
        this.positionMs = 0;
    }

    // getters/setters

    public PlayerStatus getStatus() {
        return status;
    }

    public void setStatus(PlayerStatus status) {
        this.status = status;
    }

    public int getCurrentTrackID() {
        return currentTrackID;
    }

    /**
     * @throws IllegalArgumentException if currentTrackId is negative
     */
    public void setCurrentTrackID(int currentTrackId) {
        if (currentTrackId < 0) {
            throw new IllegalArgumentException("Track ID cannot be negative");
        }
        this.currentTrackID = currentTrackId;
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
