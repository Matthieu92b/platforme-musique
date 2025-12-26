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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * DJActor : gère l'état du player pour une room.
 * Path typique : "djactor/dj-<roomId>"
 */
public class DJActor implements Actor {

    private static final Logger log = LoggerFactory.getLogger(DJActor.class);

    private String roomId;
    private PlayerStateManager state;
    private ScheduledExecutorService scheduler;

    public DJActor() {
    }

    /**
     * Constructeur compatible avec ActorSystem.actorOf(DJActor.class, "dj-" + roomId).
     * Le roomId final est déterminé dans preStart() à partir du path réel.
     */
    public DJActor(String name) {
        this.roomId = name;
    }

    /**
     * Initialisation :
     * - dérive roomId depuis le path
     * - initialise le state manager
     * - démarre un scheduler qui met à jour la position régulièrement
     */
    @Override
    public void preStart(ActorContext ctx) {
        String path = ctx.self().path();
        int idx = path.lastIndexOf('/');
        String localName = (idx >= 0) ? path.substring(idx + 1) : path;

        // Le factory crée "dj-" + roomId, donc on retire le préfixe si présent
        if (localName.startsWith("dj-")) {
            this.roomId = localName.substring("dj-".length());
        } else {
            this.roomId = localName;
        }

        this.state = new PlayerStateManager();

        // Timer interne pour avancer la position
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                state.incrementPosition();
            } catch (Exception e) {
                log.error("Error incrementing position for room {}", roomId, e);
            }
        }, 0, 250, TimeUnit.MILLISECONDS);

        log.info("DJActor started for room {}", roomId);
    }

    /**
     * Route les messages reçus vers les handlers.
     */
    @Override
    public CompletableFuture<Void> onReceive(Message message, ActorContext ctx) {
        log.info("[DJActor {}] Received type={}", roomId, message.type());

        switch (message.type()) {
            case "INIT_PLAYER" -> handleInitPlayer();
            case "STOP_PLAYER" -> handleStopPlayer();
            case "LOAD_TRACK" -> handleLoadTrack(message.payload());
            case "PLAY" -> handlePlay();
            case "PAUSE" -> handlePause();

            // La gestion du NEXT est pilotée côté djroom (playlist), ici on ignore
            case "NEXT" -> log.debug("NEXT ignored for room {} (managed by djroom)", roomId);

            case "PREV" -> handlePrev();

            // Ask local via CompletableFuture en payload
            case "GET_STATE" -> handleGetState(message.payload());

            default -> log.warn("[DJActor {}] Unknown message type: {}", roomId, message.type());
        }

        return CompletableFuture.completedFuture(null);
    }

    private void handleInitPlayer() {
        log.info("INIT_PLAYER for room {}", roomId);
        state.setIdle();
    }

    private void handleStopPlayer() {
        log.info("STOP_PLAYER for room {}", roomId);
        state.setIdle();
    }

    /**
     * Charge un track.
     * Deux formats supportés :
     * - Map (désérialisation JSON depuis un transport type Rabbit)
     * - LoadTrackMsg (appel typé local)
     */
    private void handleLoadTrack(Object payload) {
        if (payload == null) {
            log.warn("LOAD_TRACK with null payload for room {}", roomId);
            return;
        }

        long id;
        String url;
        String title;
        long durationMs;

        if (payload instanceof Map<?, ?> map) {
            try {
                id = ((Number) map.get("id")).longValue();
                url = (String) map.get("url");
                title = (String) map.get("title");
                durationMs = ((Number) map.get("durationMs")).longValue();
            } catch (Exception e) {
                log.error("Invalid LOAD_TRACK payload format for room {}: {}", roomId, payload, e);
                return;
            }
        } else if (payload instanceof LoadTrackMsg msg) {
            id = msg.id();
            url = msg.url();
            title = msg.title();
            durationMs = msg.durationMs();
        } else {
            log.error("Unsupported LOAD_TRACK payload type for room {}: {}", roomId, payload.getClass());
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

        log.info("LOAD_TRACK for room {}: title='{}', id={}", roomId, track.getTitle(), track.getId());

        // Mise à jour de la playlist interne (si ce modèle est conservé)
        state.addSongToPlaylist(
                track.getId(),
                track.getTitle(),
                track.getUrl(),
                track.getScore(),
                track.getDurationMs(),
                track.getAddedAt()
        );

        // Démarre le track comme track courant
        state.startNewSong(track);
        state.setStatus(PlayerStatus.PLAYING);
    }

    private void handlePlay() {
        log.info("PLAY for room {}", roomId);
        state.setStatus(PlayerStatus.PLAYING);
    }

    private void handlePause() {
        log.info("PAUSE for room {}", roomId);
        state.togglePlayPause();
    }

    private void handlePrev() {
        log.info("PREV for room {}", roomId);
        state.beginningOfSong();
    }

    /**
     * Complète un future passé en payload.
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
        if (current == null) {
            current = Track.EMPTY_TRACK;
        }

        Map<String, Object> snap = new HashMap<>();
        snap.put("roomId", roomId);
        snap.put("status", state.getStatus().name());
        snap.put("positionMs", state.getPositionMs());
        snap.put("queueSize", state.getPlaylist().getState().size());

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

    /**
     * Nettoyage à l'arrêt : stop du scheduler.
     */
    @Override
    public void postStop(ActorContext ctx) {
        log.info("DJActor stopped for room {}", roomId);

        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    // DTO local facultatif (utile en tests / appels typés)
    public record LoadTrackMsg(long id, String url, String title, long durationMs) {
    }
}
