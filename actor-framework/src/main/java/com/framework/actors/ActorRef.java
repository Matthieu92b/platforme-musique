package com.framework.actors;

/**
 * Reference to an actor (local or remote).
 */
public interface ActorRef {

    /**
     * Get the full path of this actor.
     */
    String path();

    /**
     * Check if this is a local actor.
     */
    boolean isLocal();

    /**
     * Send a message to this actor.
     */
    void tell(Message message, ActorRef sender);
}