package com.playistactor.playlist;

import com.playistactor.models.Track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class PlaylistActorLogic {

    private final List<Track> tracks = new ArrayList<>();

    public static final class AddTrack {
        public final Track track;
        public AddTrack(Track track) { this.track = track; }
    }

    public static final class VoteTrack {
        public final long trackId;
        public final int delta;
        public VoteTrack(long trackId, int delta) { this.trackId = trackId; this.delta = delta; }
    }

    public static final class NextTrack {
        public final boolean isNext;
        public NextTrack(boolean isNext) { this.isNext = isNext; }
    }

    public synchronized void onAddTrack(AddTrack msg) {
        tracks.add(msg.track);
        sortPlaylist();
    }

    public synchronized boolean onVoteTrack(VoteTrack msg) {
        Optional<Track> opt = tracks.stream().filter(t -> t.getId() == msg.trackId).findFirst();
        if (opt.isEmpty()) return false;
        opt.get().addScore(msg.delta);
        sortPlaylist();
        return true;
    }

    public synchronized Optional<Track> onNextTrack(NextTrack msg) {
        if (tracks.isEmpty()) return Optional.empty();
        Track next = tracks.remove(0);
        return Optional.of(next);
    }

    public synchronized List<Track> getState() {
        return Collections.unmodifiableList(new ArrayList<>(tracks));
    }

    private void sortPlaylist() {
        tracks.sort(Track.BY_SCORE_DESC_THEN_FIFO);
    }
}
