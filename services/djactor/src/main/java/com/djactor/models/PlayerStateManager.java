package com.djactor.models;

import java.time.Instant;
import java.util.Optional;

/**
 * Represents the state of a media player and contains data about the current
 * song being played. also manages skip/prev/pause actions
 * knows the playlist it's currently playing
 */
public class PlayerStateManager {
    private PlayerStatus status; // status of the player - enum
    private long currentTrackID;
    private long positionMs; // current position in milliseconds in the current song- synced with front
    private final PlaylistActorLogic playlist;

    // constructor that sets the state manager to a default status and default
    // playlist
    public PlayerStateManager() {
        this.playlist = new PlaylistActorLogic();
        setIdle();
    }

    public PlayerStateManager(PlaylistActorLogic playlist) {
        this.playlist = playlist;
        setIdle();
    }

    /**
     * performs actions to move to the next song in the playlist
     */
    public synchronized void nextSong() {
        Optional<Track> nextTrack = playlist.onNextTrack(new PlaylistActorLogic.NextTrack(true));
        if (!playlist.getState().isEmpty()) { // set idle if empty
            Track track = nextTrack.get();
            startNewSong(track.getId());
        } else {
            setIdle();
        }
        return;
    }

    /**
     * performs actions to move to the beginning of the current song by clicking the
     * prev button
     */
    public synchronized void beginningOfSong() {
        if (currentTrackID == 0) {
            return;
        }
        setPositionMs(0);
    }

    /**
     * Toggles between PLAYING and PAUSED states
     * if STOPPED, does nothing
     */
    public synchronized PlayerStatus togglePlayPause() {
        if (this.getCurrentTrackID() == 0) {
        }
        if (this.status == PlayerStatus.PLAYING) {
            this.status = PlayerStatus.PAUSED;
        } else {
            this.status = PlayerStatus.PLAYING;
        }
        return this.status;
    }

    /**
     * Starts playing a new song from the beginning
     * 
     * @param trackID ID of the track to play
     * @throws IllegalArgumentException if trackID is zero
     */
    public synchronized void startNewSong(long trackID) {
        if (trackID == 0) {
            throw new IllegalArgumentException("Track ID cannot be zero");
        }
        setCurrentTrackID(trackID);
        this.status = PlayerStatus.PLAYING;
        this.positionMs = 0;
    }

    /**
     * increments the position by 250ms
     * called by a scheduler every 250ms when playing
     */
    public void incrementPosition() {
        if (this.status == PlayerStatus.PLAYING && this.currentTrackID != 0) {
            this.positionMs += 250;

            Track currentTrack = getCurrentTrack();
            System.out.println(
                    currentTrackID + " - " + positionMs + " || " + playlist.getState() + playlist.getState().isEmpty());
            if (currentTrack != null && positionMs >= currentTrack.getDurationMs()) {
                nextSong();
            }
        }
    }

    /**
     * sets the player to idle (default) state
     */
    public void setIdle() {
        playlist.clearTracks();
        this.status = PlayerStatus.STOPPED;
        this.currentTrackID = 0;
        this.positionMs = 0;
    }

    /**
     * add a new song to the playlist
     */
    public void addSongToPlaylist(long id, String title, String url, int score, long durationMs, Instant addedAt) {
        boolean wasEmpty = playlist.getState().isEmpty();
        playlist.onAddTrack(new PlaylistActorLogic.AddTrack(new Track(id, title, url, score, durationMs, addedAt)));
        if (wasEmpty) {
            startNewSong(id); // start playing the new song if the playlist was empty
        }
    }

    // getters/setters

    public PlayerStatus getStatus() {
        return status;
    }

    public void setStatus(PlayerStatus status) {
        this.status = status;
    }

    public long getCurrentTrackID() {
        return currentTrackID;
    }

    /**
     * gets the current track being played.
     *
     * @return the current track, or null if no track is playing
     */
    public Track getCurrentTrack() {
        return playlist.getState().stream()
                .filter(track -> track.getId() == currentTrackID)
                .findFirst()
                .orElse(null);
    }

    /**
     * @throws IllegalArgumentException if currentTrackId is negative
     */
    public void setCurrentTrackID(long currentTrackId) {
        if (currentTrackId < 0) {
            throw new IllegalArgumentException("Track ID cannot be negative");
        }
        this.currentTrackID = currentTrackId;
    }

    public long getPositionMs() {
        return positionMs;
    }

    public PlaylistActorLogic getPlaylist() {
        return playlist;
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
