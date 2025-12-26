package com.djactor.models;

import java.time.Instant;
import java.util.Optional;

/**
 * Gère l'état d'un player :
 * - statut (STOPPED / PLAYING / PAUSED)
 * - track courant + position
 * - interactions next/prev/pause
 * - playlist associée (logique interne)
 */
public class PlayerStateManager {

    private PlayerStatus status;
    private Track currentTrack;
    private long positionMs;

    // Logique de playlist interne (utilisée pour déterminer le next track)
    private final PlaylistActorLogic playlist;

    /**
     * Initialise avec une playlist vide et un état idle.
     */
    public PlayerStateManager() {
        this.playlist = new PlaylistActorLogic();
        setIdle();
    }

    public PlayerStateManager(PlaylistActorLogic playlist) {
        this.playlist = playlist;
        setIdle();
    }

    /**
     * Passe au morceau suivant :
     * - si un next existe, il devient le track courant
     * - sinon, le player repasse en idle
     */
    public synchronized void nextSong() {
        Optional<Track> nextTrack = playlist.onNextTrack(new PlaylistActorLogic.NextTrack(true));

        if (nextTrack.isPresent()) {
            startNewSong(nextTrack.get());
        } else {
            setIdle();
        }
    }

    /**
     * Revenir au début du morceau courant (comportement "prev").
     * Si aucun morceau courant, ne fait rien.
     */
    public synchronized void beginningOfSong() {
        if (this.getCurrentTrack().equals(Track.EMPTY_TRACK)) {
            return;
        }
        setPositionMs(0);
    }

    /**
     * Bascule entre PLAYING et PAUSED.
     * Si aucun morceau courant, ne fait rien.
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
     * Démarre un nouveau morceau depuis 0ms.
     * Le statut passe automatiquement à PLAYING.
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
     * Incrémente la position de lecture.
     * Appelé typiquement par un scheduler (ex: toutes les 250ms).
     * Si la durée est dépassée, déclenche automatiquement nextSong().
     */
    public void incrementPosition() {
        if (this.status == PlayerStatus.PLAYING && !this.currentTrack.equals(Track.EMPTY_TRACK)) {
            this.positionMs += 250;

            Track track = getCurrentTrack();
            if (track != null && positionMs >= track.getDurationMs()) {
                nextSong();
            }
        }
    }

    /**
     * Remet le player à l'état idle :
     * - playlist vidée
     * - statut STOPPED
     * - pas de track courant
     * - position à 0
     */
    public void setIdle() {
        playlist.clearTracks();
        this.status = PlayerStatus.STOPPED;
        this.currentTrack = Track.EMPTY_TRACK;
        this.positionMs = 0;
    }

    /**
     * Ajoute un morceau à la playlist interne.
     * Si la playlist était vide et qu'aucun track n'est en cours, déclenche nextSong()
     * pour démarrer automatiquement la lecture.
     */
    public void addSongToPlaylist(long id, String title, String url, int score, long durationMs, Instant addedAt) {
        boolean wasEmpty = playlist.getState().isEmpty();

        Track track = new Track(id, title, url, score, durationMs, addedAt);
        playlist.onAddTrack(new PlaylistActorLogic.AddTrack(track));

        if (wasEmpty && this.currentTrack.equals(Track.EMPTY_TRACK)) {
            nextSong();
        }
    }

    // Getters / Setters

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
     * Sécurise l'assignation du track courant.
     * Refuse les ids négatifs (l'id 0 est géré via Track.EMPTY_TRACK).
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
     * Définit la position.
     * Si valeur négative, force à 0.
     */
    public void setPositionMs(long positionMs) {
        this.positionMs = Math.max(0, positionMs);
    }
}
