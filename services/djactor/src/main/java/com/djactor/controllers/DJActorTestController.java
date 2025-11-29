package com.djactor.controllers;

import com.djactor.config.DJActorFactory;
import com.framework.actors.ActorRef;
import com.framework.actors.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/djactor/test")
public class DJActorTestController {

    private static final Logger log = LoggerFactory.getLogger(DJActorTestController.class);

    private final DJActorFactory djActorFactory;

    public DJActorTestController(DJActorFactory djActorFactory) {
        this.djActorFactory = djActorFactory;
    }

    @PostMapping("/init/{roomId}")
    public ResponseEntity<String> init(@PathVariable String roomId) {
        log.info("[DEBUG] Init DJActor for room {}", roomId);

        ActorRef dj = djActorFactory.getOrCreateDJActor(roomId);

        // on lui envoie un INIT_PLAYER pour voir
        dj.tell(Message.of("INIT_PLAYER", null), null);

        return ResponseEntity.ok("DJActor created for room " + roomId);
    }
}
