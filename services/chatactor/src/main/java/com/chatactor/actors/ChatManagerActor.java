package com.chatactor.actors;

import com.framework.actors.Actor;
import com.framework.actors.ActorContext;
import com.framework.actors.ActorRef;
import com.framework.actors.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * ChatManagerActor :
 * - responsable de la création et de la fermeture des ChatActor
 * - un ChatActor par room
 */
public class ChatManagerActor implements Actor {

    private static final Logger log = LoggerFactory.getLogger(ChatManagerActor.class);

    public ChatManagerActor() {
        // Compatible avec une instanciation sans argument
    }

    /**
     * Constructeur requis par l'ActorSystem (signature attendue).
     * Le paramètre name n'est pas utilisé ici.
     */
    public ChatManagerActor(String name) {
    }

    /**
     * Log de démarrage pour vérifier que le manager est bien actif.
     */
    @Override
    public void preStart(ActorContext ctx) {
        log.info("ChatManagerActor started at {}", ctx.self().path());
    }

    /**
     * Gère la création et la fermeture des ChatActor.
     */
    @Override
    public CompletableFuture<Void> onReceive(Message message, ActorContext ctx) {

        switch (message.type()) {

            case "CREATE_CHAT" -> {
                String roomId = (String) message.payload();
                String actorName = "chat-" + roomId;

                try {
                    // Création du ChatActor pour la room
                    ActorRef chat = ctx.system().actorOf(ChatActor.class, actorName);
                    log.info("ChatActor created for room {} at {}", roomId, chat.path());
                } catch (Exception e) {
                    // Création idempotente : l'acteur existe déjà
                    log.info("ChatActor already exists for room {}", roomId);
                }
            }

            case "CLOSE_CHAT" -> {
                String roomId = (String) message.payload();
                String path = "chat-actor/chat-" + roomId;

                try {
                    ActorRef chat = ctx.actorSelection(path);
                    ctx.tell(chat, Message.of("CLOSE_CHAT", null));
                    log.info("CLOSE_CHAT forwarded to {}", path);
                } catch (Exception e) {
                    // Cas non bloquant : aucun chat actif pour cette room
                    log.info("No ChatActor to close for room {}", roomId);
                }
            }

            default -> log.warn("Unknown message type for ChatManagerActor: {}", message.type());
        }

        return CompletableFuture.completedFuture(null);
    }
}
