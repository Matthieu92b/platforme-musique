package com.framework.actors;

import java.util.concurrent.CompletableFuture;

/**
 * Base interface for all actors.
 * Actors process messages asynchronously and maintain their own state.
 */
public interface Actor {

    /**
     * Called when the actor receives a message.
     * Always executed on the actor's mailbox thread.
     */
    CompletableFuture<Void> onReceive(Message message, ActorContext ctx);

    /**
     * Called before the actor starts processing messages.
     */
    default void preStart(ActorContext ctx) throws Exception {}

    /**
     * Called after the actor stops.
     */
    default void postStop(ActorContext ctx) throws Exception {}

    /**
     * Called when the actor throws an exception.
     * Return the supervision directive.
     */
    default SupervisionDirective onFailure(Throwable cause, Message message) {
        return SupervisionDirective.RESTART;
    }
}