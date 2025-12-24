package com.testsupport;

import com.framework.actors.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ActorTestKit {

    private ActorTestKit() {}

    /** ActorRef de test qui capture les messages reçus */
    public static class ProbeActorRef implements ActorRef {
        private final String path;
        private final List<Envelope> received = new CopyOnWriteArrayList<>();

        public ProbeActorRef(String path) {
            this.path = path;
        }

        public record Envelope(Message message, ActorRef sender) {}

        @Override public String path() { return path; }
        @Override public boolean isLocal() { return true; }

        @Override
        public void tell(Message message, ActorRef sender) {
            received.add(new Envelope(message, sender));
        }

        public List<Envelope> received() { return received; }

        public Envelope last() {
            if (received.isEmpty()) return null;
            return received.get(received.size() - 1);
        }

        public void clear() { received.clear(); }
    }

    /** ActorRef de test (self) */
    public static class SelfActorRef implements ActorRef {
        private final String path;
        public SelfActorRef(String path) { this.path = path; }
        @Override public String path() { return path; }
        @Override public boolean isLocal() { return true; }
        @Override public void tell(Message message, ActorRef sender) { /* no-op */ }
    }

    /** ActorContext de test minimal */
    public static class FakeActorContext implements ActorContext {
        private final ActorRef self;
        private ActorRef sender;

        // Pour actorSelection()
        private final Map<String, ActorRef> selections = new HashMap<>();

        public FakeActorContext(ActorRef self) {
            this.self = self;
        }

        public void setSender(ActorRef sender) {
            this.sender = sender;
        }

        public void registerSelection(String path, ActorRef ref) {
            selections.put(path, ref);
        }

        @Override
        public void tell(ActorRef to, Message message) {
            to.tell(message, self);
        }

        @Override public ActorRef self() { return self; }
        @Override public ActorRef sender() { return sender; }

        @Override
        public ActorRef actorOf(Class<? extends Actor> actorClass, String name) {
            throw new UnsupportedOperationException("actorOf not needed in unit tests");
        }

        @Override
        public ActorRef actorSelection(String path) {
            ActorRef ref = selections.get(path);
            if (ref == null) throw new IllegalStateException("No actorSelection registered for: " + path);
            return ref;
        }

        @Override
        public void stop(ActorRef ref) { /* no-op */ }

        @Override
        public ActorSystem system() { return null; }
    }
}
