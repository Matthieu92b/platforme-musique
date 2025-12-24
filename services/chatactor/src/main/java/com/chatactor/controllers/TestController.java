package com.chatactor.controllers;

import com.chatactor.actors.ChatActor;
import com.framework.actors.ActorRef;
import com.framework.actors.ActorSystem;
import com.framework.actors.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private static final Logger log = LoggerFactory.getLogger(TestController.class);

    private final ActorSystem actorSystem;

    public TestController(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    @PostMapping("/room/{roomId}/send")
    public ResponseEntity<String> send(@PathVariable String roomId,
                                       @RequestParam(defaultValue = "Alice") String user,
                                       @RequestParam(defaultValue = "hello") String msg) {
        String path = "chat-actor/chat-" + roomId;

        try {
            ActorRef chat = actorSystem.actorSelection(path);
            chat.tell(Message.of("SEND_MESSAGE", new ChatActor.ChatMessageMsg(user, msg, roomId)), null);
            log.info("Sent SEND_MESSAGE to {}", path);
            return ResponseEntity.ok("sent to " + path);
        } catch (Exception e) {
            log.error("Failed to send to {}", path, e);
            return ResponseEntity.internalServerError().body("failed: " + e.getMessage());
        }
    }
}
