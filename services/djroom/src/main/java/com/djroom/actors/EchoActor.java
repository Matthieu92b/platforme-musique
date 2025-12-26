package com.djroom.actors;

import com.framework.actors.Actor;
import com.framework.actors.ActorContext;
import com.framework.actors.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Actor simple utilisé pour tester le framework.
 * Il reçoit un message et le renvoie tel quel à l'expéditeur.
 */
public class EchoActor implements Actor {

    // Logger associé à la classe pour tracer le cycle de vie et les messages
    private static final Logger log = LoggerFactory.getLogger(EchoActor.class);

    // Nom logique de l'acteur (utile pour le debug et les logs)
    private final String name;

    /**
     * Constructeur requis par ActorSystem.actorOf(..., "echo").
     * @param name nom de l'acteur
     */
    public EchoActor(String name) {
        this.name = name;
    }

    /**
     * Appelé automatiquement au démarrage de l'acteur.
     * Permet de vérifier que l'acteur est correctement initialisé.
     */
    @Override
    public void preStart(ActorContext ctx) {
        log.info("EchoActor '{}' started at {}", name, ctx.self().path());
    }

    /**
     * Méthode principale appelée à la réception d'un message.
     * L'acteur affiche le message reçu et le renvoie à l'expéditeur s'il existe.
     */
    @Override
    public CompletableFuture<Void> onReceive(Message message, ActorContext ctx) {

        // Log détaillé pour le debug des échanges entre acteurs
        log.info(
                "[{}:{}] Received message | type={} | payload={} | from={}",
                name,
                ctx.self().path(),
                message.type(),
                message.payload(),
                ctx.sender() != null ? ctx.sender().path() : "unknown"
        );

        // Répond uniquement si un expéditeur est défini
        if (ctx.sender() != null) {
            Message reply = Message.of("ECHO_REPLY", message.payload());
            ctx.sender().tell(reply, ctx.self());
        }

        // Aucun traitement asynchrone ici, on retourne un future déjà complété
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Appelé automatiquement lors de l'arrêt de l'acteur.
     * Utile pour tracer le cycle de vie.
     */
    @Override
    public void postStop(ActorContext ctx) {
        log.info("EchoActor '{}' stopped", name);
    }
}
