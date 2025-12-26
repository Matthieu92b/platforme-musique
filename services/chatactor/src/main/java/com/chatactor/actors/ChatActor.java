package com.chatactor.actors;

import com.chatactor.model.ChatLine;
import com.chatactor.store.ChatStore;
import com.framework.actors.Actor;
import com.framework.actors.ActorContext;
import com.framework.actors.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * ChatActor pour une seule room.
 * Nom attendu : "chat-<roomId>" (ex: "chat-room-b9a07e41").
 */
public class ChatActor implements Actor {

    private static final Logger log = LoggerFactory.getLogger(ChatActor.class);

    private String roomId;
    private final Set<String> members = new HashSet<>();

    // Historique en mémoire pour la room
    private ChatHistory history;

    public ChatActor() {
        // compatible si instanciation sans argument
    }

    public ChatActor(String name) {
        // compatible si le framework passe un nom
        // roomId est dérivé dans preStart() via ctx.self().path()
    }

    /**
     * Initialisation :
     * - instancie l'historique
     * - dérive roomId depuis le path de l'acteur
     */
    @Override
    public void preStart(ActorContext ctx) {
        this.history = new ChatHistory();

        String path = ctx.self().path();
        String localName = path.substring(path.lastIndexOf('/') + 1); // ex: chat-room-xxxx

        if (localName.startsWith("chat-")) {
            this.roomId = localName.substring("chat-".length()); // ex: room-xxxx
        } else {
            this.roomId = localName;
        }

        log.info("ChatActor started for room {} at {}", roomId, ctx.self().path());
    }

    /**
     * Route les messages de chat :
     * - USER_JOINED / USER_LEFT : gestion des membres
     * - SEND_MESSAGE : validation + stockage (history + ChatStore)
     * - CLOSE_CHAT : arrêt de l'acteur
     */
    @Override
    public CompletableFuture<Void> onReceive(Message message, ActorContext ctx) {
        log.info("[ChatActor {}] Received type={} from={}",
                roomId,
                message.type(),
                ctx.sender() != null ? ctx.sender().path() : "unknown"
        );

        switch (message.type()) {

            case "USER_JOINED" -> {
                String userId = extractString(message.payload(), "userId");
                if (userId != null) {
                    members.add(userId);
                    log.info("User joined chat room {}: {} (members={})", roomId, userId, members.size());
                } else {
                    log.warn("USER_JOINED missing userId for room {}", roomId);
                }
            }

            case "USER_LEFT" -> {
                String userId = extractString(message.payload(), "userId");
                if (userId != null) {
                    members.remove(userId);
                    log.info("User left chat room {}: {} (members={})", roomId, userId, members.size());
                } else {
                    log.warn("USER_LEFT missing userId for room {}", roomId);
                }
            }

            case "SEND_MESSAGE" -> {
                String userId = extractString(message.payload(), "userId");
                String msgRoomId = extractString(message.payload(), "roomId");
                String content = extractString(message.payload(), "message");

                // Validation minimale : évite de polluer l'historique avec des messages vides
                if (content == null || content.isBlank()) {
                    if (ctx.sender() != null) {
                        ctx.sender().tell(Message.of("CHAT_ERROR", "Empty message"), ctx.self());
                    }
                    break;
                }

                // Stockage en mémoire
                history.add(userId, msgRoomId, content);

                // Log sans emoji ; on évite aussi de trop tracer en production si ça spam
                log.info("Chat message stored (room={}, user={})", msgRoomId, userId);

                // Ack au sender
                if (ctx.sender() != null) {
                    ctx.sender().tell(
                            Message.of("CHAT_SENT", new ChatSentMsg(userId, msgRoomId)),
                            ctx.self()
                    );
                }

                // Stockage persistant / global (selon ton implémentation)
                ChatStore.get().add(
                        msgRoomId,
                        new ChatLine(userId, msgRoomId, content, System.currentTimeMillis())
                );
            }

            case "CLOSE_CHAT" -> {
                log.info("Closing chat for room {}", roomId);
                ctx.stop(ctx.self());
            }

            default -> log.warn("Unknown message type for ChatActor {}: {}", roomId, message.type());
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Log de cycle de vie.
     */
    @Override
    public void postStop(ActorContext ctx) {
        log.info("ChatActor stopped for room {}", roomId);
    }

    // ===== DTOs compatibles avec djroom.RoomActor =====

    public record UserEventMsg(String userId, String roomId) {}
    public record ChatMessageMsg(String userId, String message, String roomId) {}

    // ===== Replies =====

    public record ChatSentMsg(String userId, String roomId) {}

    /**
     * Extraction tolérante (payload Map via JSON, ou records typés en local).
     */
    private String extractString(Object payload, String key) {
        if (payload == null) return null;

        if (payload instanceof java.util.Map<?, ?> m) {
            Object v = m.get(key);
            return v == null ? null : String.valueOf(v);
        }

        if (payload instanceof UserEventMsg u) {
            return "userId".equals(key) ? u.userId()
                    : "roomId".equals(key) ? u.roomId()
                    : null;
        }

        if (payload instanceof ChatMessageMsg c) {
            return "userId".equals(key) ? c.userId()
                    : "roomId".equals(key) ? c.roomId()
                    : "message".equals(key) ? c.message()
                    : null;
        }

        return null;
    }
}
