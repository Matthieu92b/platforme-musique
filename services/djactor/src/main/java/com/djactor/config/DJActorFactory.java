package com.djactor.config;

import com.djactor.actors.DJActor;
import com.framework.actors.ActorRef;
import com.framework.actors.ActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Factory to manage DJActor instances (one per room)
 */
@Component
public class DJActorFactory {

    private static final Logger log = LoggerFactory.getLogger(DJActorFactory.class);

    private final ActorSystem actorSystem;
    private final ConcurrentMap<String, ActorRef> djActors = new ConcurrentHashMap<>();

    public DJActorFactory(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Get or create a DJActor for a specific room
     */
    public ActorRef getOrCreateDJActor(String roomId) {
        return djActors.computeIfAbsent(roomId, id -> {
            log.info("Creating DJActor for room: {}", roomId);
            return actorSystem.actorOf(DJActor.class, "dj-" + roomId);
        });
    }

    /**
     * Remove a DJActor (when room closes)
     */
    public void removeDJActor(String roomId) {
        ActorRef actor = djActors.remove(roomId);
        if (actor != null) {
            actorSystem.stop(actor);
            log.info("Removed DJActor for room: {}", roomId);
        }
    }

    /**
     * Get existing DJActor (or null if not exists)
     */
    public ActorRef getDJActor(String roomId) {
        return djActors.get(roomId);
    }
}