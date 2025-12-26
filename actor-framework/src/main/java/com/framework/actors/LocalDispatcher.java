package com.framework.actors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatcher local :
 * - route les messages vers les acteurs locaux
 * - chaque acteur possède une mailbox et un thread dédié (single-thread executor)
 */
public class LocalDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LocalDispatcher.class);

    private final Map<String, Actor> actors = new ConcurrentHashMap<>();
    private final Map<String, ActorContext> contexts = new ConcurrentHashMap<>();
    private final Map<String, Mailbox> mailboxes = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> executors = new ConcurrentHashMap<>();

    // Conservé tel quel, même si non utilisé : peut servir à une évolution future
    private final ExecutorService eventLoop = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "actor-event-loop");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean running = new AtomicBoolean(true);

    // Sender courant stocké dans un ThreadLocal, utilisé par ActorContext.sender()
    private static final ThreadLocal<ActorRef> CURRENT_SENDER = new ThreadLocal<>();

    /**
     * Enregistre un acteur local :
     * - crée sa mailbox
     * - démarre son message pump (boucle de traitement)
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

        log.debug("Actor registered: {}", path);
    }

    /**
     * Désenregistre un acteur :
     * - supprime les références
     * - appelle postStop() pour permettre le nettoyage côté acteur
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
     * Dépose un message dans la mailbox d'un acteur.
     * Si l'acteur n'existe pas, on loggue un warn et on ignore.
     */
    public void dispatch(String path, Message message, ActorRef sender) {
        Mailbox mailbox = mailboxes.get(path);
        if (mailbox == null) {
            log.warn("Cannot dispatch to unknown actor: {}", path);
            log.debug("Available actors: {}", mailboxes.keySet());
            return;
        }

        mailbox.enqueue(message, sender);
    }

    /**
     * Démarre la boucle de traitement des messages d'un acteur.
     * Le traitement est séquentiel (single-thread) pour garantir l'absence de concurrence sur l'état de l'acteur.
     */
    private void startMessagePump(String path, ExecutorService exec) {
        exec.submit(() -> {
            Mailbox mailbox = mailboxes.get(path);
            Actor actor = actors.get(path);
            ActorContext context = contexts.get(path);

            if (mailbox == null || actor == null || context == null) {
                return;
            }

            log.debug("Message pump started for {}", path);

            while (running.get() && actors.containsKey(path)) {
                Mailbox.Envelope envelope = null;

                try {
                    envelope = mailbox.poll(250, TimeUnit.MILLISECONDS);
                    if (envelope == null) {
                        continue;
                    }

                    // Expose le sender à ActorContext.sender() via ThreadLocal
                    CURRENT_SENDER.set(envelope.sender());
                    try {
                        actor.onReceive(envelope.message(), context).join();
                    } finally {
                        CURRENT_SENDER.remove();
                    }

                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;

                } catch (Throwable t) {
                    log.error("Actor '{}' failed while handling message type={}",
                            path,
                            envelope != null ? envelope.message().type() : "unknown",
                            t
                    );

                    if (envelope != null) {
                        SupervisionDirective directive = actor.onFailure(t, envelope.message());
                        handleSupervision(path, actor, context, directive);
                    }
                }
            }

            log.debug("Message pump stopped for {}", path);
        });
    }

    /**
     * Applique la directive de supervision renvoyée par l'acteur.
     */
    private void handleSupervision(String path, Actor actor, ActorContext context, SupervisionDirective directive) {
        switch (directive) {
            case RESUME -> {
                // Reprendre sans rien faire : le prochain message sera traité normalement
                log.debug("Actor {} resumed after failure", path);
            }
            case RESTART -> {
                // Redémarrage : postStop puis preStart pour réinitialiser l'état si nécessaire
                log.info("Actor {} restarting after failure", path);
                try {
                    actor.postStop(context);
                    actor.preStart(context);
                } catch (Exception e) {
                    log.error("Failed to restart actor {}", path, e);
                }
            }
            case STOP -> {
                // Arrêt : on désenregistre l'acteur
                log.info("Actor {} stopping after failure", path);
                unregister(path);
            }
            case ESCALATE -> {
                // Pas de supervision hiérarchique ici : on loggue en erreur
                log.error("Actor {} escalated failure", path);
            }
        }
    }

    /**
     * Retourne le sender du message actuellement en cours de traitement.
     */
    public ActorRef getCurrentSender() {
        return CURRENT_SENDER.get();
    }

    /**
     * Arrête le dispatcher local.
     */
    public void shutdown() {
        running.set(false);
        eventLoop.shutdownNow();

        // Optionnel : arrêter aussi les executors par acteur
        executors.values().forEach(ExecutorService::shutdownNow);
        executors.clear();
    }

    /**
     * Mailbox : file d'attente des messages d'un acteur.
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
