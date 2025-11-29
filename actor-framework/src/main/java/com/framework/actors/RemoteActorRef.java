package com.framework.actors;

class RemoteActorRef implements ActorRef {
    private final String path;
    private final ActorSystem system;

    RemoteActorRef(String path, ActorSystem system) {
        this.path = path;
        this.system = system;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public void tell(Message message, ActorRef sender) {
        if (system != null) {
            system.tell(this, message, sender);
        }
    }

    @Override
    public String toString() {
        return "RemoteActorRef(" + path + ")";
    }
}