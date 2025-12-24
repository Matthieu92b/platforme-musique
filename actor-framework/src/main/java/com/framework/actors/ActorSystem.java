package com.framework.actors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The actor system manages actors and provides communication infrastructure.
 */
public class ActorSystem implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ActorSystem.class);

    private final String serviceName;
    private final LocalDispatcher localDispatcher;
    private final RemoteDispatcher remoteDispatcher;
    private final Map<String, ActorRef> actors = new ConcurrentHashMap<>();

    /**
     * Create a new actor system.
     */
    public static ActorSystem create(String serviceName, RabbitTemplate rabbitTemplate) {
        return new ActorSystem(serviceName, rabbitTemplate);
    }

    private ActorSystem(String serviceName, RabbitTemplate rabbitTemplate) {
        this.serviceName = serviceName;
        this.localDispatcher = new LocalDispatcher();

        // Initialize remote dispatcher with connection factory
        ConnectionFactory connectionFactory = rabbitTemplate.getConnectionFactory();
        this.remoteDispatcher = new RemoteDispatcher(
                serviceName,
                rabbitTemplate,
                connectionFactory,
                localDispatcher,
                this
        );

        log.info("🚀 Actor system '{}' started", serviceName);
    }

    /**
     * Create a new actor in this system.
     */
    public ActorRef actorOf(Class<? extends Actor> actorClass, String name) {
        String fullPath = serviceName + "/" + name;

        if (actors.containsKey(fullPath)) {
            throw new IllegalStateException("Actor already exists: " + fullPath);
        }

        try {
            // 1️⃣ Crée l'instance d'acteur
            Actor actor = actorClass.getDeclaredConstructor(String.class).newInstance(name);

            // 2️⃣ Crée sa référence locale
            LocalActorRef ref = new LocalActorRef(fullPath, this);

            // 3️⃣ Construit le context
            ActorContext context = new ActorContextImpl(ref, this, localDispatcher);

            // 4️⃣ ➜ IMPORTANT : on appelle preStart AVANT d'enregistrer l'acteur
            //     Comme ça, preStart() peut utiliser ctx.self(), ctx.actorSelection(), etc.
            actor.preStart(context);

            // 5️⃣ Maintenant seulement on le rend "public" dans le système :
            //     - on l'ajoute aux acteurs
            //     - on l'enregistre dans le dispatcher (ce qui démarre la message pump)
            actors.put(fullPath, ref);
            localDispatcher.register(fullPath, actor, context);

            log.info("✅ Actor created: {}", fullPath);
            return ref;

        } catch (Exception e) {
            log.error("❌ Failed to create actor: {}", name, e);
            throw new RuntimeException("Failed to create actor: " + name, e);
        }
    }

    /**
     * Select an actor by path (local or remote).
     */
    public ActorRef actorSelection(String path) {
        // If no slash, assume local
        if (!path.contains("/")) {
            path = serviceName + "/" + path;
        }

        String targetService = path.split("/")[0];

        if (targetService.equals(serviceName)) {
            // Local actor
            ActorRef ref = actors.get(path);
            if (ref == null) {
                log.warn("⚠️  Local actor not found: {}", path);
                log.warn("     Available: {}", actors.keySet());
                throw new IllegalArgumentException("Actor not found: " + path);
            }
            return ref;
        } else {
            // Remote actor
            log.debug("🌐 Creating remote reference: {}", path);
            return new RemoteActorRef(path, this);
        }
    }

    /**
     * Send a message to an actor.
     */
    public void tell(ActorRef target, Message message, ActorRef sender) {
        if (target instanceof LocalActorRef) {
            localDispatcher.dispatch(target.path(), message, sender);
        } else if (target instanceof RemoteActorRef) {
            remoteDispatcher.send(target.path(), message, sender);
        }
    }

    /**
     * Stop an actor.
     */
    public void stop(ActorRef ref) {
        if (ref instanceof LocalActorRef) {
            localDispatcher.unregister(ref.path());
            actors.remove(ref.path());
            log.info("🛑 Actor stopped: {}", ref.path());
        }
    }

    public String getServiceName() {
        return serviceName;
    }

    @Override
    public void close() {
        log.info("🔴 Shutting down actor system '{}'", serviceName);
        localDispatcher.shutdown();
        remoteDispatcher.shutdown();
        actors.clear();
    }
}
