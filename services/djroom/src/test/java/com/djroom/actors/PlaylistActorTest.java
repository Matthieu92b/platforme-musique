package com.djroom.actors;

import com.djroom.models.PlaylistTrack;
import com.framework.actors.Message;
import com.testsupport.ActorTestKit.*;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PlaylistActorTest {

    private PlaylistActor actor;
    private FakeActorContext ctx;
    private ProbeActorRef senderProbe;
    private ProbeActorRef djActorProbe;

    @BeforeEach
    void setup() throws Exception {
        actor = new PlaylistActor();

        // self path important: preStart() parse "djroom/playlist-<roomId>"
        ctx = new FakeActorContext(new SelfActorRef("djroom/playlist-room-123"));
        senderProbe = new ProbeActorRef("test/sender");
        djActorProbe = new ProbeActorRef("djactor/dj-room-123");

        // actorSelection utilisé pour notifyDJActorLoadTrack()
        ctx.registerSelection("djactor/dj-room-123", djActorProbe);

        actor.preStart(ctx);
    }

    @Test
    void addTrack_shouldReplyTrackAdded_andNotifyDJActor_ifFirstTrack() {
        ctx.setSender(senderProbe);

        var msg = new PlaylistActor.AddTrackMsg("url1", "t1", 1000, "matthieu");
        actor.onReceive(Message.of("ADD_TRACK", msg), ctx).join();

        // 1) reply au sender
        var last = senderProbe.last();
        assertNotNull(last);
        assertEquals("TRACK_ADDED", last.message().type());

        // 2) notify DJActor (LOAD_TRACK) parce que c'est le 1er track
        var djLast = djActorProbe.last();
        assertNotNull(djLast);
        assertEquals("LOAD_TRACK", djLast.message().type());

        // payload = LoadTrackMsg
        Object payload = djLast.message().payload();
        assertTrue(payload instanceof PlaylistActor.LoadTrackMsg);
        PlaylistActor.LoadTrackMsg load = (PlaylistActor.LoadTrackMsg) payload;

        assertEquals("url1", load.url());
        assertEquals("t1", load.title());
        assertEquals(1000L, load.durationMs());
    }

    @Test
    void voteTrack_shouldIncreaseScore_andKeepNonNegative() {
        ctx.setSender(senderProbe);

        actor.onReceive(Message.of("ADD_TRACK", new PlaylistActor.AddTrackMsg("url1", "t1", 1000, "u1")), ctx).join();

        senderProbe.clear();
        actor.onReceive(Message.of("VOTE_TRACK", new PlaylistActor.VoteTrackMsg(1, +1)), ctx).join();

        var last = senderProbe.last();
        assertNotNull(last);
        assertEquals("VOTE_SUCCESS", last.message().type());

        PlaylistActor.VoteSuccessMsg payload = (PlaylistActor.VoteSuccessMsg) last.message().payload();
        assertEquals(1L, payload.trackId());
        assertEquals(1, payload.newScore());
    }

    @Test
    void getNextTrack_shouldRemoveTop_andNotifyDJActor_andReply() {
        ctx.setSender(senderProbe);

        // add 2 tracks
        actor.onReceive(Message.of("ADD_TRACK", new PlaylistActor.AddTrackMsg("url1", "t1", 1000, "u1")), ctx).join();
        actor.onReceive(Message.of("ADD_TRACK", new PlaylistActor.AddTrackMsg("url2", "t2", 1000, "u2")), ctx).join();

        // vote track 2 to make it top
        actor.onReceive(Message.of("VOTE_TRACK", new PlaylistActor.VoteTrackMsg(2, +10)), ctx).join();

        senderProbe.clear();
        djActorProbe.clear();

        // next => should pick track2 then remove it
        actor.onReceive(Message.of("GET_NEXT_TRACK", null), ctx).join();

        // sender receives NEXT_TRACK
        var reply = senderProbe.last();
        assertNotNull(reply);
        assertEquals("NEXT_TRACK", reply.message().type());

        PlaylistActor.NextTrackMsg nextPayload = (PlaylistActor.NextTrackMsg) reply.message().payload();
        assertEquals("t2", nextPayload.track().getTitle());

        // DJActor receives LOAD_TRACK for track2
        var dj = djActorProbe.last();
        assertNotNull(dj);
        assertEquals("LOAD_TRACK", dj.message().type());
        PlaylistActor.LoadTrackMsg load = (PlaylistActor.LoadTrackMsg) dj.message().payload();
        assertEquals("t2", load.title());
    }

    @Test
    void getPlaylist_shouldCompleteFuture_snapshot() throws Exception {
        // add tracks
        ctx.setSender(senderProbe);
        actor.onReceive(Message.of("ADD_TRACK", new PlaylistActor.AddTrackMsg("url1", "t1", 1000, "u1")), ctx).join();
        actor.onReceive(Message.of("ADD_TRACK", new PlaylistActor.AddTrackMsg("url2", "t2", 2000, "u2")), ctx).join();

        CompletableFuture<PlaylistActor.PlaylistStateMsg> fut = new CompletableFuture<>();

        // GET_PLAYLIST payload = future => doit compléter
        actor.onReceive(Message.of("GET_PLAYLIST", fut), ctx).join();

        PlaylistActor.PlaylistStateMsg state = fut.get(1, TimeUnit.SECONDS);
        assertNotNull(state);

        List<PlaylistTrack> tracks = state.tracks();
        assertEquals(2, tracks.size());
        assertEquals("t1", tracks.get(0).getTitle());
        assertEquals("t2", tracks.get(1).getTitle());
    }
}
