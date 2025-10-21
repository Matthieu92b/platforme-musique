// src/main/java/com/djroom/app/actors/ActorCtx.java
package com.djroom.app.actors;

public interface ActorCtx {
    /** Envoie un message asynchrone à un autre acteur. */
    void tell(ActorRef to, Message message);

    /** Référence vers l’acteur courant. */
    ActorRef self();
}
