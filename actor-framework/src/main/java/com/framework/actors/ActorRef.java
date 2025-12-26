package com.framework.actors;

/**
 * Référence vers un acteur (local ou distant).
 * Une ActorRef permet d’interagir avec un acteur
 * sans exposer son implémentation interne.
 */
public interface ActorRef {

    /**
     * Retourne le chemin complet (path) de l’acteur.
     * Exemple : "djroom/playlist-room-xxxx".
     */
    String path();

    /**
     * Indique si l’acteur est local au système courant.
     */
    boolean isLocal();

    /**
     * Envoie un message à cet acteur.
     *
     * @param message message à envoyer
     * @param sender  référence de l’acteur expéditeur (peut être null)
     */
    void tell(Message message, ActorRef sender);
}
