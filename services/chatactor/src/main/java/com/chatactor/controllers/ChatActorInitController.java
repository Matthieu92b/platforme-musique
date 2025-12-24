package com.chatactor.controllers;

import com.chatactor.actors.ChatActor;
import com.framework.actors.ActorRef;
import com.framework.actors.ActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatActorInitController {

    private static final Logger log = LoggerFactory.getLogger(ChatActorInitController.class);

    private final ActorSystem actorSystem;

    public ChatActorInitController(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Create ChatActor for a room
     * POST /api/chat/init/{roomId}
     * -> creates actor "chat-<roomId>"
     */
    @PostMapping("/init/{roomId}")
    public ResponseEntity<String> initChat(@PathVariable String roomId) {
        String actorName = "chat-" + roomId;

        try {
            ActorRef ref = actorSystem.actorOf(ChatActor.class, actorName);
            log.info("[CHAT-ACTOR] Created {}", ref.path());
            return ResponseEntity.ok("ChatActor created: " + ref.path());
        } catch (Exception e) {
            log.error("Failed to create ChatActor {}", actorName, e);
            return ResponseEntity.internalServerError().body("Failed to create ChatActor: " + actorName);
        }
    }
}
