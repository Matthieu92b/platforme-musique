package com.djactor.actors;

import com.djactor.models.PlayerStateManager;
import com.djactor.models.PlayerStatus;
import com.djactor.models.Track;
import com.framework.actors.Actor;
import com.framework.actors.ActorContext;
import com.framework.actors.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * DJActor : gère le player d'une seule room.
 * Path typique : "djactor/dj-<roomId>"
 */
public class DJActor implements Actor {

    private static final Logger log = LoggerFactory.getLogger(DJActor.class);

    private String roomId;
    private PlayerStateManager state;
    private ScheduledExecutorService scheduler;

    // Constructeur par défaut (pas obligatoire, mais ok)
    public DJActor() {}

    // Constructeur requis par ton framework : ActorSystem.actorOf(DJActor.class, "dj-" + roomId)
    public DJActor(String name) {
        this.roomId = name; // la vraie extraction se fait dans preStart()
    }

    @Override
    public void preStart(ActorContext ctx) {
        // path ex: "djactor/dj-room-e765eb71" ou "djactor/dj-room-xxxx"
        String path = ctx.self().path();
        int idx = path.lastIndexOf('/');
        String localName = (idx >= 0) ? path.substring(idx + 1) : path; // ex: "dj-room-xxx" ou "dj-room-xxx"

        // Ton DJActorFactory crée "dj-" + roomId, donc localName commence par "dj-"
        if (localName.startsWith("dj-")) {
            this.roomId = localName.substring("dj-".length()); // "room-xxxx"
        } else {
            this.roomId = localName;
        }

        this.state = new PlayerStateManager();

        // Timer interne pour avancer la position toutes les 250ms
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                state.incrementPosition();
            } catch (Exception e) {
                log.error("Error incrementing position for room {}", roomId, e);
            }
        }, 0, 250, TimeUnit.MILLISECONDS);

        log.info("🎧 DJActor started for room {}", roomId);
    }

    @Override
    public CompletableFuture<Void> onReceive(Message message, ActorContext ctx) {
        log.info("[DJActor {}] Received type={}", roomId, message.type());

        switch (message.type()) {
            case "INIT_PLAYER" -> handleInitPlayer();
            case "STOP_PLAYER" -> handleStopPlayer();
            case "LOAD_TRACK" -> handleLoadTrack(message.payload());
            case "PLAY" -> handlePlay();
            case "PAUSE" -> handlePause();
            case "NEXT" -> log.info("NEXT ignored (managed by djroom) for room {}", roomId);

            case "PREV" -> handlePrev();

            // ✅ NOUVEAU : ask local via CompletableFuture en payload
            case "GET_STATE" -> handleGetState(message.payload());

            default -> log.warn("[DJActor {}] Unknown message type: {}", roomId, message.type());
        }

        return CompletableFuture.completedFuture(null);
    }

    private void handleInitPlayer() {
        log.info("🟢 INIT_PLAYER for room {}", roomId);
        state.setIdle();
    }

    private void handleStopPlayer() {
        log.info("🔴 STOP_PLAYER for room {}", roomId);
        state.setIdle();
    }

    @SuppressWarnings("unchecked")
    private void handleLoadTrack(Object payload) {
        if (payload == null) {
            log.warn("LOAD_TRACK with null payload for room {}", roomId);
            return;
        }

        long id;
        String url;
        String title;
        long durationMs;

        // Cas des messages JSON venus de djroom via Rabbit (désérialisés en Map)
        if (payload instanceof Map<?, ?> map) {
            try {
                id = ((Number) map.get("id")).longValue();
                url = (String) map.get("url");
                title = (String) map.get("title");
                durationMs = ((Number) map.get("durationMs")).longValue();
            } catch (Exception e) {
                log.error("Invalid LOAD_TRACK payload format: {}", payload, e);
                return;
            }
        }
        // Cas d'un envoi local typé
        else if (payload instanceof LoadTrackMsg msg) {
            id = msg.id();
            url = msg.url();
            title = msg.title();
            durationMs = msg.durationMs();
        } else {
            log.error("Unsupported LOAD_TRACK payload type: {} for room {}", payload.getClass(), roomId);
            return;
        }

        Track track = new Track(
                id,
                title,
                url,
                0,
                durationMs,
                Instant.now()
        );

        log.info("📥 LOAD_TRACK in room {}: {} (id={})", roomId, track.getTitle(), track.getId());

        // On ajoute en playlist interne (si tu gardes ce modèle)
        state.addSongToPlaylist(
                track.getId(),
                track.getTitle(),
                track.getUrl(),
                track.getScore(),
                track.getDurationMs(),
                track.getAddedAt()
        );

        // Si aucun currentTrack, on démarre
        state.startNewSong(track);
        state.setStatus(PlayerStatus.PLAYING);
    }

    private void handlePlay() {
        log.info("▶️ PLAY command for room {}", roomId);
        state.setStatus(PlayerStatus.PLAYING);
    }

    private void handlePause() {
        log.info("⏸️ PAUSE command for room {}", roomId);
        state.togglePlayPause();
    }

    private void handleNext() {
        log.info("⏭️ NEXT command for room {}", roomId);
        state.nextSong();
    }

    private void handlePrev() {
        log.info("⏮️ PREV command for room {}", roomId);
        state.beginningOfSong();
    }

    /**
     * ✅ GET_STATE : complète un future passé en payload
     * Payload attendu : CompletableFuture<Map<String,Object>>
     */
    private void handleGetState(Object payload) {
        if (!(payload instanceof CompletableFuture<?> future)) {
            log.warn("[DJActor {}] GET_STATE expects CompletableFuture payload", roomId);
            return;
        }

        @SuppressWarnings("unchecked")
        CompletableFuture<Map<String, Object>> typed = (CompletableFuture<Map<String, Object>>) future;

        Track current = state.getCurrentTrack();
        if (current == null) current = Track.EMPTY_TRACK;

        Map<String, Object> snap = new HashMap<>();
        snap.put("roomId", roomId);
        snap.put("status", state.getStatus().name());
        snap.put("positionMs", state.getPositionMs());
        snap.put("queueSize", state.getPlaylist().getState().size());

        // Track courante
        if (!current.equals(Track.EMPTY_TRACK)) {
            snap.put("trackId", current.getId());
            snap.put("currentTitle", current.getTitle());
            snap.put("currentUrl", current.getUrl());
            snap.put("durationMs", current.getDurationMs());
        } else {
            snap.put("trackId", null);
            snap.put("currentTitle", null);
            snap.put("currentUrl", null);
            snap.put("durationMs", 0L);
        }

        typed.complete(snap);
    }

    @Override
    public void postStop(ActorContext ctx) {
        log.info("🛑 DJActor stopped for room {}", roomId);
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    // DTO local facultatif si tu veux tester en interne
    public record LoadTrackMsg(long id, String url, String title, long durationMs) {}
}
