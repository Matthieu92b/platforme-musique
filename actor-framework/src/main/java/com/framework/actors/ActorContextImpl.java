package com.framework.actors;

class ActorContextImpl implements ActorContext {
    private final ActorRef self;
    private final ActorSystem system;
    private final LocalDispatcher localDispatcher;

    ActorContextImpl(ActorRef self, ActorSystem system, LocalDispatcher localDispatcher) {
        this.self = self;
        this.system = system;
        this.localDispatcher = localDispatcher;
    }

    @Override
    public void tell(ActorRef to, Message message) {
        system.tell(to, message, self);
    }

    @Override
    public ActorRef self() {
        return self;
    }

    @Override
    public ActorRef sender() {
        return localDispatcher.getCurrentSender();
    }

    @Override
    public ActorRef actorOf(Class<? extends Actor> actorClass, String name) {
        return system.actorOf(actorClass, name);
    }

    @Override
    public ActorRef actorSelection(String path) {
        return system.actorSelection(path);
    }

    @Override
    public void stop(ActorRef ref) {
        system.stop(ref);
    }

    @Override
    public ActorSystem system() {
        return system;
    }
}