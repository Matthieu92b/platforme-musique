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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * DJActor : gère le player d'une seule room.
 *
 * Path typique : "djactor/dj-room-xxxx"
 */
public class DJActor implements Actor {

    private static final Logger log = LoggerFactory.getLogger(DJActor.class);

    private String roomId;
    private PlayerStateManager state;
    private ScheduledExecutorService scheduler;

    // 🔹 Constructeur par défaut (utilisé par le framework si besoin)
    public DJActor() {
    }

    // 🔹 Constructeur requis par le framework : ActorSystem.actorOf(DJActor.class, "dj-room-XXXX")
    public DJActor(String name) {
        if (name != null && name.startsWith("dj-room-")) {
            this.roomId = name.substring("dj-room-".length());
        } else {
            this.roomId = name;
        }
    }

    @Override
    public void preStart(ActorContext ctx) {
        // path ex: "djactor/dj-room-e765eb71"
        String path = ctx.self().path();
        int idx = path.lastIndexOf('/');
        String localName = (idx >= 0) ? path.substring(idx + 1) : path; // dj-room-e765eb71

        if (localName.startsWith("dj-")) {
            this.roomId = localName.substring("dj-".length());  // "room-xxxx"
        } else {
            this.roomId = localName;
        }

        this.state = new PlayerStateManager();

        // Timer interne pour avancer la position toutes les 250ms
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        state.incrementPosition();
                    } catch (Exception e) {
                        log.error("Error incrementing position for room {}", roomId, e);
                    }
                },
                0,
                250,
                TimeUnit.MILLISECONDS
        );

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
            case "NEXT" -> handleNext();
            case "PREV" -> handlePrev();
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
        // Cas d'un envoi local éventuellement typé
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
                (int) id,  // adapte si ton Track utilise un long
                title,
                url,
                0,
                durationMs,
                Instant.now()
        );

        log.info("📥 LOAD_TRACK in room {}: {} (id={})", roomId, track.getTitle(), track.getId());

        state.addSongToPlaylist(
                track.getId(),
                track.getTitle(),
                track.getUrl(),
                track.getScore(),
                track.getDurationMs(),
                track.getAddedAt()
        );

        if (state.getCurrentTrack() == null) {
            state.startNewSong(track);
        }
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
