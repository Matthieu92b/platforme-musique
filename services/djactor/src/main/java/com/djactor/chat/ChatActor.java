package com.djactor.chat;

import com.framework.actors.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChatActor implements Actor {

    private final String name;
    private ChatHistory history;

    public ChatActor(String name) {
        this.name = name;
    }

    @Override
    public void preStart(ActorContext ctx) {
        this.history = new ChatHistory();
        System.out.println("ChatActor started: " + name);
    }

    @Override
    public CompletableFuture<Void> onReceive(Message message, ActorContext ctx) {

        switch (message.type()) {

            case "SEND_MESSAGE" -> {
                String[] payload = (String[]) message.payload();
                String user = payload[0];
                String content = payload[1];

                history.addMessage(user, content);
                System.out.println("Message reçu: " + user + " -> " + content);
            }

            case "GET_LAST_MESSAGES" -> {
                int limit = (int) message.payload();
                List<ChatHistory.ChatEntry> last = history.getLastMessages(limit);

                System.out.println("Historique (" + last.size() + " messages):");
                last.forEach(System.out::println);
            }

            default -> System.out.println("Message inconnu: " + message.type());
        }

        return CompletableFuture.completedFuture(null);
    }
}
