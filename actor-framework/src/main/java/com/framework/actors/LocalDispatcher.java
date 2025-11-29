package com.framework.actors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatches messages to local actors using mailboxes.
 */
public class LocalDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LocalDispatcher.class);

    private final Map<String, Actor> actors = new ConcurrentHashMap<>();
    private final Map<String, ActorContext> contexts = new ConcurrentHashMap<>();
    private final Map<String, Mailbox> mailboxes = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> executors = new ConcurrentHashMap<>();
    private final ExecutorService eventLoop = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "actor-event-loop");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean running = new AtomicBoolean(true);
    private static final ThreadLocal<ActorRef> CURRENT_SENDER = new ThreadLocal<>();

    /**
     * Register a new actor.
     */
    public void register(String path, Actor actor, ActorContext context) {
        if (actors.putIfAbsent(path, actor) != null) {
            throw new IllegalStateException("Actor already registered: " + path);
        }

        contexts.put(path, context);
        mailboxes.put(path, new Mailbox());

        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "actor-" + path);
            t.setDaemon(true);
            return t;
        });
        executors.put(path, exec);

        startMessagePump(path, exec);

        log.debug("📝 Actor registered: {}", path);
    }

    /**
     * Unregister an actor.
     */
    public void unregister(String path) {
        Actor actor = actors.remove(path);
        ActorContext context = contexts.remove(path);
        mailboxes.remove(path);

        if (actor != null && context != null) {
            try {
                actor.postStop(context);
            } catch (Exception e) {
                log.error("Error in postStop for actor {}", path, e);
            }
        }
    }

    /**
     * Dispatch a message to an actor.
     */
    public void dispatch(String path, Message message, ActorRef sender) {
        Mailbox mailbox = mailboxes.get(path);
        if (mailbox == null) {
            log.warn("⚠️  Cannot dispatch to unknown actor: {}", path);
            log.warn("     Available actors: {}", mailboxes.keySet());
            return;
        }

        log.debug("📬 Dispatching {} to {}", message.type(), path);
        mailbox.enqueue(message, sender);
    }

    /**
     * Start message processing loop for an actor.
     */
    private void startMessagePump(String path, ExecutorService exec) {
        exec.submit(() -> {
            Mailbox mailbox = mailboxes.get(path);
            Actor actor = actors.get(path);
            ActorContext context = contexts.get(path);

            if (mailbox == null || actor == null || context == null) {
                return;
            }

            log.debug("🔄 Message pump started for: {}", path);

            while (running.get() && actors.containsKey(path)) {
                Mailbox.Envelope envelope = null;
                try {
                    envelope = mailbox.poll(250, TimeUnit.MILLISECONDS);
                    if (envelope == null) {
                        continue;
                    }

                    CURRENT_SENDER.set(envelope.sender());
                    try {
                        log.debug("📨 Processing {} for {}", envelope.message().type(), path);
                        actor.onReceive(envelope.message(), context).join();
                    } finally {
                        CURRENT_SENDER.remove();
                    }

                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;

                } catch (Throwable t) {
                    log.error("❌ Actor '{}' failed on {}: {}",
                            path,
                            envelope != null ? envelope.message().type() : "unknown",
                            t.getMessage());

                    if (envelope != null) {
                        SupervisionDirective directive = actor.onFailure(t, envelope.message());
                        handleSupervision(path, actor, context, directive, t);
                    }
                }
            }

            log.debug("⏹️  Message pump stopped for: {}", path);
        });
    }

    /**
     * Handle supervision directive.
     */
    private void handleSupervision(String path, Actor actor, ActorContext context,
                                   SupervisionDirective directive, Throwable cause) {
        switch (directive) {
            case RESUME -> log.debug("↩️  Actor {} resuming after failure", path);
            case RESTART -> {
                log.info("🔄 Actor {} restarting after failure", path);
                try {
                    actor.postStop(context);
                    actor.preStart(context);
                } catch (Exception e) {
                    log.error("Failed to restart actor {}", path, e);
                }
            }
            case STOP -> {
                log.info("🛑 Actor {} stopping after failure", path);
                unregister(path);
            }
            case ESCALATE -> log.error("⬆️  Actor {} escalating failure", path);
        }
    }

    /**
     * Get current sender (for ActorContext.sender()).
     */
    public ActorRef getCurrentSender() {
        return CURRENT_SENDER.get();
    }

    public void shutdown() {
        running.set(false);
        eventLoop.shutdownNow();
    }

    /**
     * Mailbox for actor messages.
     */
    static class Mailbox {
        private final BlockingQueue<Envelope> queue = new LinkedBlockingQueue<>();

        record Envelope(Message message, ActorRef sender) {}

        void enqueue(Message message, ActorRef sender) {
            queue.offer(new Envelope(message, sender));
        }

        Envelope poll(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }
    }
}