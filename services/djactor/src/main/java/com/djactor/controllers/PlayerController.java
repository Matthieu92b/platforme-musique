package com.djactor.controllers;

import com.djactor.actors.DJActor;
import com.djactor.config.DJActorFactory;
import com.framework.actors.ActorRef;
import com.framework.actors.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Optional REST controller for direct player queries (monitoring/debugging)
 */
@RestController
@RequestMapping("/api/player")
public class PlayerController {

    private static final Logger log = LoggerFactory.getLogger(PlayerController.class);

    private final DJActorFactory djActorFactory;

    public PlayerController(DJActorFactory djActorFactory) {
        this.djActorFactory = djActorFactory;
    }

    /**
     * Get player state for a room
     * GET /api/player/{roomId}/state
     */
    @GetMapping("/{roomId}/state")
    public ResponseEntity<?> getPlayerState(@PathVariable String roomId) {
        ActorRef djActor = djActorFactory.getOrCreateDJActor(roomId);

        if (djActor == null) {
            return ResponseEntity.notFound().build();
        }

        // For now, return basic info
        // In real implementation, would use ask pattern to get state
        return ResponseEntity.ok(new PlayerStateResponse(
                roomId,
                "UNKNOWN", // Would get from actor
                "Active"
        ));
    }

    /**
     * Health check
     * GET /api/player/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("DJActor service is running");
    }

    record PlayerStateResponse(String roomId, String status, String state) {}
}