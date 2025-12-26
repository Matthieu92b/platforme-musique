package com.framework.actors;

import java.util.concurrent.CompletableFuture;

/**
 * Interface de base pour tous les acteurs.
 * Un acteur traite des messages de manière asynchrone
 * et conserve son propre état interne.
 */
public interface Actor {

    /**
     * Appelée lorsqu’un message est reçu par l’acteur.
     * Cette méthode est toujours exécutée dans le thread
     * associé à la mailbox de l’acteur.
     */
    CompletableFuture<Void> onReceive(Message message, ActorContext ctx);

    /**
     * Appelée avant que l’acteur ne commence à traiter des messages.
     * Utile pour l’initialisation (état, ressources, acteurs enfants, etc.).
     */
    default void preStart(ActorContext ctx) throws Exception {}

    /**
     * Appelée après l’arrêt de l’acteur.
     * Utile pour libérer les ressources (threads, connexions, etc.).
     */
    default void postStop(ActorContext ctx) throws Exception {}

    /**
     * Appelée lorsqu’une exception est levée pendant le traitement d’un message.
     * Permet de définir la stratégie de supervision à appliquer
     * (ex: RESTART, STOP, RESUME).
     */
    default SupervisionDirective onFailure(Throwable cause, Message message) {
        return SupervisionDirective.RESTART;
    }
}
