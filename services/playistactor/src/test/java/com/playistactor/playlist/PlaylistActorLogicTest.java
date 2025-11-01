package com.playistactor.playlist;

import com.playistactor.models.Track;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PlaylistActorLogicTest {

    @Test
    void addAndOrderByScoreThenFifoAndNext() {
        PlaylistActorLogic logic = new PlaylistActorLogic();

        Track t1 = new Track(1, "A", "urlA", 5, Instant.parse("2025-01-01T10:00:00Z"));
        Track t2 = new Track(2, "B", "urlB", 5, Instant.parse("2025-01-01T10:00:01Z"));
        Track t3 = new Track(3, "C", "urlC", 3, Instant.parse("2025-01-01T10:00:02Z"));

        logic.onAddTrack(new PlaylistActorLogic.AddTrack(t2));
        logic.onAddTrack(new PlaylistActorLogic.AddTrack(t1));
        logic.onAddTrack(new PlaylistActorLogic.AddTrack(t3));

        List<Track> state = logic.getState();
        assertEquals(3, state.size());
        assertEquals(1, state.get(0).getId());
        assertEquals(2, state.get(1).getId());
        assertEquals(3, state.get(2).getId());

        boolean voted = logic.onVoteTrack(new PlaylistActorLogic.VoteTrack(3, 3));
        assertTrue(voted);

        state = logic.getState();
        assertEquals(3, state.size());
        assertEquals(3, state.get(0).getId());

        Optional<Track> next = logic.onNextTrack(new PlaylistActorLogic.NextTrack(true));
        assertTrue(next.isPresent());
        assertEquals(3, next.get().getId());

        state = logic.getState();
        assertEquals(2, state.size());
        assertEquals(1, state.get(0).getId());
        assertEquals(2, state.get(1).getId());
    }

    @Test
    void voteNonExistingReturnsFalse() {
        PlaylistActorLogic logic = new PlaylistActorLogic();
        boolean voted = logic.onVoteTrack(new PlaylistActorLogic.VoteTrack(999, 1));
        assertFalse(voted);
    }
}
