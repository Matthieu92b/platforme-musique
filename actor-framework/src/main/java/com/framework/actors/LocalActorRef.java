package com.framework.actors;

class LocalActorRef implements ActorRef {
    private final String path;
    private final ActorSystem system;

    LocalActorRef(String path, ActorSystem system) {
        this.path = path;
        this.system = system;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public void tell(Message message, ActorRef sender) {
        system.tell(this, message, sender);
    }

    @Override
    public String toString() {
        return "LocalActorRef(" + path + ")";
    }
}