package com.djroom.controllers;

import com.framework.actors.ActorRef;
import com.framework.actors.ActorSystem;
import com.framework.actors.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test/remote")
public class RemoteDjactorTestController {

    private static final Logger log = LoggerFactory.getLogger(RemoteDjactorTestController.class);

    private final ActorSystem actorSystem;

    public RemoteDjactorTestController(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    @PostMapping("/init/{roomId}")
    public ResponseEntity<String> initRemote(@PathVariable String roomId) {
        String path = "djactor/dj-" + roomId;

        log.info("[DJROOM] Test remote: sending INIT_PLAYER to {}", path);

        ActorRef remote = actorSystem.actorSelection(path);
        remote.tell(Message.of("INIT_PLAYER", null), null);

        return ResponseEntity.ok("INIT_PLAYER sent to " + path);
    }
}
