package com.djactor.actors;

import com.framework.actors.Message;
import com.testsupport.ActorTestKit.*;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DJActorTest {

    private DJActor actor;
    private FakeActorContext ctx;

    @BeforeEach
    void setup() throws Exception {
        actor = new DJActor();

        // preStart parse "djactor/dj-<roomId>"
        ctx = new FakeActorContext(new SelfActorRef("djactor/dj-room-123"));
        actor.preStart(ctx);
    }

    @AfterEach
    void tearDown() throws Exception {
        actor.postStop(ctx);
    }

    @Test
    void loadTrack_shouldSetCurrent_andPlaying_andPosition0() {
        // payload type "LoadTrackMsg" (record interne DJActor)
        DJActor.LoadTrackMsg payload = new DJActor.LoadTrackMsg(1L, "url1", "t1", 1000L);

        actor.onReceive(Message.of("LOAD_TRACK", payload), ctx).join();

        // ask GET_STATE -> future
        CompletableFuture<Map<String, Object>> fut = new CompletableFuture<>();
        actor.onReceive(Message.of("GET_STATE", fut), ctx).join();

        Map<String, Object> snap = fut.join();

        assertEquals("room-123", snap.get("roomId"));
        assertEquals("PLAYING", snap.get("status"));
        assertEquals("t1", snap.get("currentTitle"));
        assertEquals("url1", snap.get("currentUrl"));
        assertEquals(0L, ((Number) snap.get("positionMs")).longValue());
    }

    @Test
    void pause_shouldTogglePlayingPaused() throws Exception {
        actor.onReceive(Message.of("LOAD_TRACK", new DJActor.LoadTrackMsg(1L, "url1", "t1", 1000L)), ctx).join();

        // pause toggles
        actor.onReceive(Message.of("PAUSE", null), ctx).join();

        CompletableFuture<Map<String, Object>> fut = new CompletableFuture<>();
        actor.onReceive(Message.of("GET_STATE", fut), ctx).join();

        Map<String, Object> snap = fut.get(1, TimeUnit.SECONDS);
        assertEquals("PAUSED", snap.get("status"));
    }

    @Test
    void getState_shouldReturnEmptyTrack_whenNoTrackLoaded() throws Exception {
        CompletableFuture<Map<String, Object>> fut = new CompletableFuture<>();
        actor.onReceive(Message.of("GET_STATE", fut), ctx).join();

        Map<String, Object> snap = fut.get(1, TimeUnit.SECONDS);

        assertEquals("room-123", snap.get("roomId"));
        assertNotNull(snap.get("status"));
        assertNull(snap.get("currentUrl"));
        assertEquals(0L, ((Number) snap.get("durationMs")).longValue());
    }
}
