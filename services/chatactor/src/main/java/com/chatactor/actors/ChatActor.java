package com.chatactor.actors;

import com.framework.actors.Actor;
import com.framework.actors.ActorContext;
import com.framework.actors.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.chatactor.model.ChatLine;
import com.chatactor.store.ChatStore;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * ChatActor for ONE room.
 * Actor name expected: "chat-<roomId>".
 */
public class ChatActor implements Actor {

    private static final Logger log = LoggerFactory.getLogger(ChatActor.class);

    private String roomId;
    private final Set<String> members = new HashSet<>();
    private ChatHistory history;

    public ChatActor() {
        // compatible si ton framework instancie sans arg
    }

    public ChatActor(String name) {
        // compatible si ton framework passe un "name" (comme EchoActor)
        // on parse dans preStart via ctx.self().path()
    }

    @Override
    public void preStart(ActorContext ctx) {
        this.history = new ChatHistory();

        // path ex: "chat-actor/chat-room-b9a07e41"
        String path = ctx.self().path();
        String localName = path.substring(path.lastIndexOf('/') + 1); // chat-room-xxxx
        if (localName.startsWith("chat-")) {
            this.roomId = localName.substring("chat-".length()); // room-xxxx
        } else {
            this.roomId = localName;
        }

        log.info("💬 ChatActor started for room {} at {}", roomId, ctx.self().path());
    }

    @Override
    public CompletableFuture<Void> onReceive(Message message, ActorContext ctx) {
        log.info("[ChatActor {}] Received type={} from={}",
                roomId,
                message.type(),
                ctx.sender() != null ? ctx.sender().path() : "unknown");

        switch (message.type()) {

            case "USER_JOINED" -> {
                String userId = extractString(message.payload(), "userId");
                members.add(userId);
                log.info("👤 user joined chat room {}: {} (members={})", roomId, userId, members.size());
            }

            case "USER_LEFT" -> {
                String userId = extractString(message.payload(), "userId");
                members.remove(userId);
                log.info("👋 user left chat room {}: {} (members={})", roomId, userId, members.size());
            }

            case "SEND_MESSAGE" -> {
                String userId = extractString(message.payload(), "userId");
                String msgRoomId = extractString(message.payload(), "roomId");
                String content = extractString(message.payload(), "message");

                if (content == null || content.isBlank()) {
                    if (ctx.sender() != null) {
                        ctx.sender().tell(Message.of("CHAT_ERROR", "Empty message"), ctx.self());
                    }
                    break;
                }

                history.add(userId, msgRoomId, content);
                log.info("📩 [{}] {}: {}", msgRoomId, userId, content);

                if (ctx.sender() != null) {
                    ctx.sender().tell(Message.of("CHAT_SENT", new ChatSentMsg(userId, msgRoomId)), ctx.self());
                }
                ChatStore.get().add(msgRoomId, new ChatLine(userId, msgRoomId, content, System.currentTimeMillis()));

            }

            case "CLOSE_CHAT" -> {
                log.info("🔒 Closing chat for room {}", roomId);
                ctx.stop(ctx.self());
            }

            default -> log.warn("Unknown message type for ChatActor {}: {}", roomId, message.type());
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void postStop(ActorContext ctx) {
        log.info("💬 ChatActor stopped for room {}", roomId);
    }

    // ===== DTOs compatibles avec djroom.RoomActor =====
    public record UserEventMsg(String userId, String roomId) {}
    public record ChatMessageMsg(String userId, String message, String roomId) {}

    // ===== Replies =====
    public record ChatSentMsg(String userId, String roomId) {}
    private String extractString(Object payload, String key) {
        if (payload == null) return null;

        if (payload instanceof java.util.Map<?, ?> m) {
            Object v = m.get(key);
            return v == null ? null : String.valueOf(v);
        }

        // fallback si jamais c'est bien tes records en local
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
