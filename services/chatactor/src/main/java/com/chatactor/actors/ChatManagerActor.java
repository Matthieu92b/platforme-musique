package com.chatactor.actors;

import com.framework.actors.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class ChatManagerActor implements Actor {

    private static final Logger log = LoggerFactory.getLogger(ChatManagerActor.class);
    public ChatManagerActor() {
        // pour compat si framework supporte no-arg
    }

    public ChatManagerActor(String name) {
        // IMPORTANT: ton ActorSystem cherche ce constructeur
        // (même si tu n’utilises pas 'name' ici)
    }
    @Override
    public void preStart(ActorContext ctx) {
        log.info("🧠 ChatManagerActor started at {}", ctx.self().path());
    }

    @Override
    public CompletableFuture<Void> onReceive(Message message, ActorContext ctx) {

        switch (message.type()) {

            case "CREATE_CHAT" -> {
                String roomId = (String) message.payload();  // room-7c9bf514
                String actorName = "chat-" + roomId;         // => chat-room-7c9bf514 ✅
                ctx.system().actorOf(ChatActor.class, actorName);

                try {
                    // ✅ UTILISE TON API OFFICIELLE
                    ActorRef chat = ctx.system().actorOf(ChatActor.class, actorName);
                    log.info("✅ ChatActor created for room {} -> {}", roomId, chat.path());
                } catch (Exception e) {
                    // idempotent : déjà créé
                    log.info("ℹ️ ChatActor already exists for room {}", roomId);
                }
            }

            case "CLOSE_CHAT" -> {
                String roomId = (String) message.payload();
                String path = "chat-actor/chat-" + roomId;

                try {
                    ActorRef chat = ctx.actorSelection(path);
                    ctx.tell(chat, Message.of("CLOSE_CHAT", null));
                    log.info("🔒 CLOSE_CHAT forwarded to {}", path);
                } catch (Exception e) {
                    log.info("ℹ️ No chat actor to close for room {}", roomId);
                }
            }

            default -> log.warn("Unknown message type: {}", message.type());
        }

        return CompletableFuture.completedFuture(null);
    }
}
