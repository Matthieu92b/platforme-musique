package com.djactor.models;

/**
 * Represents the state of a media player and contains data about the current
 * song being played. also manages skip/prev/pause actions
 * knows the playlist it's currently playing
 */
public class PlayerStateManager {
    private PlayerStatus status; // status of the player - enum
    private int currentTrackID;
    private long positionMs; // current position in milliseconds in the current song- synced with front
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
    public synchronized void nextSong(boolean test) {
        if (test) // if next song in play list or no playlist
            startNewSong(0); // TODO : add next song in playlist
        else {
            setIdle();
        }
    }

    /**
     * performs actions to move to the prev song
     */
    public synchronized void prevSong(boolean test) {
        if (test || getPositionMs() < 3000) // if no prev song in play list or if at the beginning of the song (<3s)
            setPositionMs(0);
        else if (test) {
            startNewSong(0); // TODO : play prev in playlist
        } else {
            setIdle();
        }
    }

    /**
     * Toggles between PLAYING and PAUSED states
     * if STOPPED, does nothing
     */
    public synchronized void togglePlayPause() { // TODO check if theres a song
        if (this.status == PlayerStatus.PLAYING)
            this.status = PlayerStatus.PAUSED;
        else
            this.status = PlayerStatus.PLAYING;
    }

    /**
     * Starts playing a new song from the beginning
     * 
     * @param trackID ID of the track to play
     */
    public synchronized void startNewSong(int trackID) {
        setCurrentTrackID(trackID);
        this.status = PlayerStatus.PLAYING;
        this.positionMs = 0;
    }

    /**
     * increments the position by 250ms
     * called by a scheduler every 250ms when playing
     */
    public void incrementPosition() {
        if (this.status == PlayerStatus.PLAYING) {
            this.positionMs += 250;
        }
        // TODO : if (positionMs >= trackDuration){ nextSong();}
    }

    /**
     * sets the player to idle (default) state
     */
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

    public long getPositionMs() {
        return positionMs;
    }

    /**
     * sets the position in milliseconds, zero if negative
     */
    public void setPositionMs(long positionMs) {
        if (positionMs < 0) {
            this.positionMs = 0;
        } else {
            this.positionMs = positionMs;
        }
    }
}
