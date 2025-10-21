// src/main/java/com/djroom/app/actors/EchoActor.java
package com.djroom.app.actors;

import java.util.concurrent.CompletableFuture;

public final class EchoActor implements Actor {
    @Override
    public CompletableFuture<Void> on(Message message, ActorCtx ctx) {
        System.out.printf("[Echo:%s] type=%s payload=%s%n",
                ctx.self().name(), message.type(), message.payload());
        return CompletableFuture.completedFuture(null);
    }
}
