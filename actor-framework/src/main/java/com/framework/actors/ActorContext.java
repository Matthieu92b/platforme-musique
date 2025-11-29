package com.framework.actors;

/**
 * Context provided to actors for interacting with the actor system.
 */
public interface ActorContext {

    /**
     * Send a message to another actor.
     */
    void tell(ActorRef to, Message message);

    /**
     * Get reference to self.
     */
    ActorRef self();

    /**
     * Get reference to the sender of the current message.
     */
    ActorRef sender();

    /**
     * Create a child actor.
     */
    ActorRef actorOf(Class<? extends Actor> actorClass, String name);

    /**
     * Select an actor by path (local or remote).
     */
    ActorRef actorSelection(String path);

    /**
     * Stop an actor.
     */
    void stop(ActorRef ref);

    /**
     * Get the actor system.
     */
    ActorSystem system();
}