package com.chatactor.store;

import com.chatactor.model.ChatLine;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ChatStore {

    private static final ChatStore INSTANCE = new ChatStore();

    public static ChatStore get() {
        return INSTANCE;
    }

    private final ConcurrentMap<String, CopyOnWriteArrayList<ChatLine>> map = new ConcurrentHashMap<>();

    private ChatStore() {}

    public void add(String roomId, ChatLine line) {
        map.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>()).add(line);
        System.out.println("[ChatStore] add room=" + roomId + " msg=" + line.message());
    }

    public List<ChatLine> history(String roomId) {
        return map.getOrDefault(roomId, new CopyOnWriteArrayList<>());
    }
}
