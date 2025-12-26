package com.framework.actors;

/**
 * Contexte fourni à un acteur pour interagir avec le système d’acteurs.
 * Il permet d’envoyer des messages, de créer ou arrêter des acteurs,
 * et d’accéder aux références utiles (self, sender, system).
 */
public interface ActorContext {

    /**
     * Envoie un message à un autre acteur.
     */
    void tell(ActorRef to, Message message);

    /**
     * Retourne la référence de l’acteur courant.
     */
    ActorRef self();

    /**
     * Retourne la référence de l’expéditeur du message courant.
     * Peut être null si le message a été envoyé sans sender.
     */
    ActorRef sender();

    /**
     * Crée un acteur enfant avec le nom donné.
     */
    ActorRef actorOf(Class<? extends Actor> actorClass, String name);

    /**
     * Sélectionne un acteur à partir de son path (local ou distant).
     */
    ActorRef actorSelection(String path);

    /**
     * Arrête l’acteur référencé.
     */
    void stop(ActorRef ref);

    /**
     * Retourne le système d’acteurs.
     */
    ActorSystem system();
}
