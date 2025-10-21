package com.djroom.app.actors;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

final class Mailbox {
    private final BlockingQueue<Message> queue = new LinkedBlockingQueue<>();

    void enqueue(Message m) { queue.offer(m); }

    /** Récupère un message ou null si le timeout expire. */
    Message poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    int size() { return queue.size(); }
}
