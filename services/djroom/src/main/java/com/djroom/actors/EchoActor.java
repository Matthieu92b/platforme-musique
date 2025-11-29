package com.djroom.actors;

import com.framework.actors.Actor;
import com.framework.actors.ActorContext;
import com.framework.actors.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Simple echo actor for testing the framework.
 */
public class EchoActor implements Actor {

    private static final Logger log = LoggerFactory.getLogger(EchoActor.class);

    private final String name;

    // ✅ Constructeur exigé par ActorSystem.actorOf(..., "echo")
    public EchoActor(String name) {
        this.name = name;
    }

    @Override
    public void preStart(ActorContext ctx) {
        log.info("EchoActor '{}' started at {}", name, ctx.self().path());
    }

    @Override
    public CompletableFuture<Void> onReceive(Message message, ActorContext ctx) {
        log.info("[{}:{}] Received: type={}, payload={}, from={}",
                name,
                ctx.self().path(),
                message.type(),
                message.payload(),
                ctx.sender() != null ? ctx.sender().path() : "unknown"
        );

        // Echo back to sender if present
        if (ctx.sender() != null) {
            Message reply = Message.of("ECHO_REPLY", message.payload());
            ctx.sender().tell(reply, ctx.self());
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void postStop(ActorContext ctx) {
        log.info("EchoActor '{}' stopped", name);
    }
}
