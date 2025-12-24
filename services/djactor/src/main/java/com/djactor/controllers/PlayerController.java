package com.djactor.controllers;

import com.djactor.config.DJActorFactory;
import com.framework.actors.ActorRef;
import com.framework.actors.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/player")
public class PlayerController {

    private static final Logger log = LoggerFactory.getLogger(PlayerController.class);

    private final DJActorFactory djActorFactory;

    public PlayerController(DJActorFactory djActorFactory) {
        this.djActorFactory = djActorFactory;
    }

    /**
     * GET /api/player/{roomId}/state
     * -> ask local (CompletableFuture payload) au DJActor
     */
    @GetMapping("/{roomId}/state")
    public ResponseEntity<?> getPlayerState(@PathVariable String roomId) {
        try {
            ActorRef djActor = djActorFactory.getOrCreateDJActor(roomId);

            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
            djActor.tell(Message.of("GET_STATE", future), null);

            Map<String, Object> payload = future.get(2, TimeUnit.SECONDS);

            // Option: mapper proprement dans un record pour un JSON stable
            String status = (String) payload.getOrDefault("status", "UNKNOWN");
            String currentTitle = (String) payload.get("currentTitle");
            String currentUrl = (String) payload.get("currentUrl");

            long positionMs = ((Number) payload.getOrDefault("positionMs", 0L)).longValue();
            long durationMs = ((Number) payload.getOrDefault("durationMs", 0L)).longValue();
            int queueSize = ((Number) payload.getOrDefault("queueSize", 0)).intValue();

            PlayerStateResponse response = new PlayerStateResponse(
                    roomId,
                    status,
                    currentTitle,
                    currentUrl,
                    positionMs,
                    durationMs,
                    queueSize
            );

            return ResponseEntity.ok(response);

        } catch (TimeoutException e) {
            log.warn("Timeout waiting for player state for room {}", roomId);
            return ResponseEntity.status(504).body("Timeout waiting for player state");
        } catch (Exception e) {
            log.error("Error while getting player state", e);
            return ResponseEntity.internalServerError().body("Error while getting player state");
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("DJActor service is running");
    }

    public record PlayerStateResponse(
            String roomId,
            String status,
            String currentTitle,
            String currentUrl,
            long positionMs,
            long durationMs,
            int queueSize
    ) {}
}
