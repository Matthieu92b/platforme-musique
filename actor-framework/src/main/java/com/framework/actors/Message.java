package com.framework.actors;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable message passed between actors.
 */
public final class Message implements Serializable {

    private final String type;
    private final Object payload;
    private final String correlationId;
    private final String senderPath;

    public Message(String type, Object payload, String correlationId, String senderPath) {
        this.type = Objects.requireNonNull(type, "Message type cannot be null");
        this.payload = payload;
        this.correlationId = (correlationId == null || correlationId.isBlank())
                ? UUID.randomUUID().toString()
                : correlationId;
        this.senderPath = senderPath;
    }

    // Factory methods
    public static Message of(String type, Object payload) {
        return new Message(type, payload, null, null);
    }

    public static Message of(String type, Object payload, ActorRef sender) {
        return new Message(type, payload, null, sender != null ? sender.path() : null);
    }

    // Getters
    public String type() { return type; }
    public Object payload() { return payload; }
    public String correlationId() { return correlationId; }
    public String senderPath() { return senderPath; }

    @Override
    public String toString() {
        return "Message{type='" + type + "', correlationId='" + correlationId + "'}";
    }
}