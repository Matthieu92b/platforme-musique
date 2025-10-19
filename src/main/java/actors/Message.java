package actors;

import java.util.Objects;
import java.util.UUID;

public final class Message {
    private final String type;
    private final Object payload;
    private final String correlationId;
    private final String sender;

    public Message(String type, Objects payload,String correlationId,String sender ) {
        this.type = Objects.requireNonNull(type);
        this.payload = payload;
        this.correlationId = (correlationId == null || correlationId.isBlank())
                ? UUID.randomUUID().toString()
                : correlationId;
        this.sender = sender;
    }
    public String type() { return type; }
    public Object payload() { return payload; }
    public String correlationId() { return correlationId; }
    public String sender() { return sender; }

    public static Message of(String type, Objects payload) {
        return new Message(type, payload, null, null);
    }
}
