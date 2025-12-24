package com.chatactor.actors;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ChatHistory {

    public record ChatEntry(String userId, String roomId, String message, Instant timestamp) {}

    private final List<ChatEntry> entries = new ArrayList<>();

    public synchronized void add(String userId, String roomId, String message) {
        entries.add(new ChatEntry(userId, roomId, message, Instant.now()));
    }

    public synchronized List<ChatEntry> last(int limit) {
        int size = entries.size();
        if (limit >= size) return new ArrayList<>(entries);
        return new ArrayList<>(entries.subList(size - limit, size));
    }

    public synchronized int size() {
        return entries.size();
    }
}
