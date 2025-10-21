// src/main/java/com/djroom/app/actors/Actor.java
package com.djroom.app.actors;

import java.util.concurrent.CompletableFuture;

public interface Actor {
    /** Reçoit un message (toujours sur le thread du dispatcher). */
    CompletableFuture<Void> on(Message message, ActorCtx ctx);
}
