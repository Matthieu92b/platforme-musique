package com.djroom.controllers;

import com.djroom.actors.EchoActor;
import com.framework.actors.ActorRef;
import com.framework.actors.ActorSystem;
import com.framework.actors.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private static final Logger log = LoggerFactory.getLogger(TestController.class);

    private final ActorSystem actorSystem;

    public TestController(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Simple test:
     * - crée un EchoActor
     * - lui envoie un message
     * - tu vérifies dans les logs que tout est passé
     *
     * GET /api/test/echo?msg=hello
     */
    @GetMapping("/echo")
    public ResponseEntity<String> echo(@RequestParam(defaultValue = "hello") String msg) {

        String actorName = "echo-test-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Creating EchoActor: {}", actorName);

        ActorRef echoActor = actorSystem.actorOf(EchoActor.class, actorName);

        Message message = Message.of("TEST_ECHO", msg);
        log.info("Sending TEST_ECHO to {}", echoActor.path());

        actorSystem.tell(echoActor, message, null);


        return ResponseEntity.ok("EchoActor created and message sent. Check logs for output.");
    }
}
