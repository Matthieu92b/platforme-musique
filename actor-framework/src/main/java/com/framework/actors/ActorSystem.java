package com.framework.actors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le système d’acteurs est responsable de :
 * - la création des acteurs
 * - leur enregistrement
 * - l’acheminement des messages (local ou distant)
 * - la gestion du cycle de vie
 */
public class ActorSystem implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ActorSystem.class);

    // Nom logique du service (ex: "djroom", "djactor", "chat-actor")
    private final String serviceName;

    // Dispatcher local (acteurs dans le même service)
    private final LocalDispatcher localDispatcher;

    // Dispatcher distant (communication inter-services via RabbitMQ)
    private final RemoteDispatcher remoteDispatcher;

    // Registre des acteurs locaux (path -> ActorRef)
    private final Map<String, ActorRef> actors = new ConcurrentHashMap<>();

    /**
     * Méthode de fabrique pour créer un ActorSystem.
     */
    public static ActorSystem create(String serviceName, RabbitTemplate rabbitTemplate) {
        return new ActorSystem(serviceName, rabbitTemplate);
    }

    private ActorSystem(String serviceName, RabbitTemplate rabbitTemplate) {
        this.serviceName = serviceName;
        this.localDispatcher = new LocalDispatcher();

        // Initialisation du dispatcher distant à partir de RabbitMQ
        ConnectionFactory connectionFactory = rabbitTemplate.getConnectionFactory();
        this.remoteDispatcher = new RemoteDispatcher(
                serviceName,
                rabbitTemplate,
                connectionFactory,
                localDispatcher,
                this
        );

        log.info("Actor system '{}' started", serviceName);
    }

    /**
     * Crée un acteur local dans ce système.
     *
     * @param actorClass classe de l’acteur
     * @param name       nom logique de l’acteur
     */
    public ActorRef actorOf(Class<? extends Actor> actorClass, String name) {
        String fullPath = serviceName + "/" + name;

        if (actors.containsKey(fullPath)) {
            throw new IllegalStateException("Actor already exists: " + fullPath);
        }

        try {
            // 1) Instanciation de l’acteur (constructeur Actor(String name))
            Actor actor = actorClass.getDeclaredConstructor(String.class).newInstance(name);

            // 2) Création de la référence locale
            LocalActorRef ref = new LocalActorRef(fullPath, this);

            // 3) Construction du contexte d’exécution
            ActorContext context = new ActorContextImpl(ref, this, localDispatcher);

            // 4) Appel de preStart AVANT l’enregistrement
            //    Cela permet à l’acteur d’utiliser ctx.self(), ctx.actorSelection(), etc.
            actor.preStart(context);

            // 5) Enregistrement officiel dans le système
            actors.put(fullPath, ref);
            localDispatcher.register(fullPath, actor, context);

            log.info("Actor created: {}", fullPath);
            return ref;

        } catch (Exception e) {
            log.error("Failed to create actor {}", name, e);
            throw new RuntimeException("Failed to create actor: " + name, e);
        }
    }

    /**
     * Sélectionne un acteur par son path (local ou distant).
     *
     * @param path path complet ou relatif
     */
    public ActorRef actorSelection(String path) {
        // Si aucun service n’est précisé, on suppose local
        if (!path.contains("/")) {
            path = serviceName + "/" + path;
        }

        String targetService = path.split("/")[0];

        if (targetService.equals(serviceName)) {
            // Acteur local
            ActorRef ref = actors.get(path);
            if (ref == null) {
                log.warn("Local actor not found: {}", path);
                log.warn("Available actors: {}", actors.keySet());
                throw new IllegalArgumentException("Actor not found: " + path);
            }
            return ref;
        } else {
            // Acteur distant (création d’une référence distante)
            log.debug("Creating remote actor reference: {}", path);
            return new RemoteActorRef(path, this);
        }
    }

    /**
     * Envoie un message à un acteur (local ou distant).
     */
    public void tell(ActorRef target, Message message, ActorRef sender) {
        if (target instanceof LocalActorRef) {
            localDispatcher.dispatch(target.path(), message, sender);
        } else if (target instanceof RemoteActorRef) {
            remoteDispatcher.send(target.path(), message, sender);
        }
    }

    /**
     * Arrête un acteur local.
     */
    public void stop(ActorRef ref) {
        if (ref instanceof LocalActorRef) {
            localDispatcher.unregister(ref.path());
            actors.remove(ref.path());
            log.info("Actor stopped: {}", ref.path());
        }
    }

    public String getServiceName() {
        return serviceName;
    }

    /**
     * Arrêt propre du système :
     * - arrêt des dispatchers
     * - nettoyage du registre d’acteurs
     */
    @Override
    public void close() {
        log.info("Shutting down actor system '{}'", serviceName);
        localDispatcher.shutdown();
        remoteDispatcher.shutdown();
        actors.clear();
    }
}
