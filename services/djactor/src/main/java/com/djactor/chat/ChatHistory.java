package com.djactor.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simple in-memory chat history.
 */
public class ChatHistory {

    public static class ChatEntry {
        private final String user;
        private final String content;
        private final Instant timestamp;

        public ChatEntry(String user, String content) {
            this.user = user;
            this.content = content;
            this.timestamp = Instant.now();
        }

        public String getUser() {
            return user;
        }

        public String getContent() {
            return content;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "[" + timestamp + "] " + user + " : " + content;
        }
    }

    private final List<ChatEntry> messages = new ArrayList<>();

    public synchronized void addMessage(String user, String content) {
        messages.add(new ChatEntry(user, content));
    }

    public synchronized List<ChatEntry> getLastMessages(int limit) {
        int size = messages.size();
        if (limit >= size) {
            return new ArrayList<>(messages);
        }
        return new ArrayList<>(messages.subList(size - limit, size));
    }

    public synchronized int size() {
        return messages.size();
    }
}
