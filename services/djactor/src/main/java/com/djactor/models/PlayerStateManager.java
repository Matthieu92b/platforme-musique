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
    private Track currentTrack;
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

        if (nextTrack.isPresent()) {
            Track track = nextTrack.get();
            startNewSong(track);
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
        if (this.getCurrentTrack().equals(Track.EMPTY_TRACK)) {
            return;
        }
        setPositionMs(0);
    }

    /**
     * Toggles between PLAYING and PAUSED states
     * if STOPPED, does nothing
     */
    public synchronized PlayerStatus togglePlayPause() {
        if (this.getCurrentTrack().equals(Track.EMPTY_TRACK)) {
            return this.status;
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
     * @throws IllegalArgumentException if trackID is zero
     */
    public synchronized void startNewSong(Track track) {
        if (track.equals(Track.EMPTY_TRACK)) {
            throw new IllegalArgumentException("Track ID cannot be zero");
        }
        setCurrentTrack(track);
        this.status = PlayerStatus.PLAYING;
        this.positionMs = 0;
    }

    /**
     * increments the position by 250ms
     * called by a scheduler every 250ms when playing
     */
    public void incrementPosition() {
        if (this.status == PlayerStatus.PLAYING && !this.currentTrack.equals(Track.EMPTY_TRACK)) {
            this.positionMs += 250;

            Track currentTrack = getCurrentTrack();
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
        this.currentTrack = Track.EMPTY_TRACK;
        this.positionMs = 0;
    }

    /**
     * add a new song to the playlist
     */
    public void addSongToPlaylist(long id, String title, String url, int score, long durationMs, Instant addedAt) {
        boolean wasEmpty = playlist.getState().isEmpty();
        Track track = new Track(id, title, url, score, durationMs, addedAt);
        playlist.onAddTrack(new PlaylistActorLogic.AddTrack(track));

        if (wasEmpty && this.currentTrack.equals(Track.EMPTY_TRACK)) {
            nextSong(); // start playing the new song if the playlist was empty and the current song too
        }
    }

    // getters/setters

    public PlayerStatus getStatus() {
        return status;
    }

    public void setStatus(PlayerStatus status) {
        this.status = status;
    }

    public Track getCurrentTrack() {
        return this.currentTrack;
    }

    /**
     * @throws IllegalArgumentException if currentTrackId is negative
     */
    public void setCurrentTrack(Track currentTrack) {
        if (currentTrack.getId() < 0) {
            throw new IllegalArgumentException("Track ID cannot be zero or negative");
        }
        this.currentTrack = currentTrack;
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
