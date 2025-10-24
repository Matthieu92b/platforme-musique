package com.djroom.app.actors;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Dispatcher implements ActorCtx, AutoCloseable {

    private final Map<String, Actor> actors = new ConcurrentHashMap<>();
    private final Map<String, Mailbox> mailboxes = new ConcurrentHashMap<>();

    private final ExecutorService loop = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "actor-loop");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean running = new AtomicBoolean(true);
    private static final ThreadLocal<ActorRef> CURRENT = new ThreadLocal<>();

    public void register(String name, Actor actor) {
        if (actors.putIfAbsent(name, actor) != null) {
            throw new IllegalStateException("Actor already exists: " + name);
        }
        mailboxes.put(name, new Mailbox());
        submitPump(name);
    }

    private void submitPump(String name) {
        loop.submit(() -> {
            final var mailbox = mailboxes.get(name);
            final var actor = actors.get(name);
            if (mailbox == null || actor == null) return;

            while (running.get() && actors.containsKey(name)) {
                Message m = null;
                try {
                    m = mailbox.poll(250, TimeUnit.MILLISECONDS);
                    if (m == null) continue;

                    CURRENT.set(new ActorRef(name));
                    try {
                        actor.on(m, this).join();
                    } finally {
                        CURRENT.remove();
                    }
                } catch (InterruptedException ie) {

                    Thread.currentThread().interrupt();
                    break;
                } catch (Throwable t) {
                    System.err.println("Actor '" + name + "' failed on "
                            + (m != null ? m.type() : "<no message>") + ": " + t);
                }
            }
        });
    }

    // -------- ActorCtx --------
    @Override
    public void tell(ActorRef to, Message message) {
        Mailbox mb = mailboxes.get(to.name());
        if (mb == null) throw new IllegalArgumentException("Unknown actor: " + to.name());
        mb.enqueue(message);
    }

    @Override
    public ActorRef self() {
        ActorRef ref = CURRENT.get();
        if (ref == null) throw new IllegalStateException("No actor context");
        return ref;
    }


    public void tell(String to, Message message) { tell(new ActorRef(to), message); }

    public boolean exists(String name) { return actors.containsKey(name); }

    @Override
    public void close() {
        running.set(false);
        loop.shutdownNow();
    }
}
