package com.djactor.chat;

import com.framework.actors.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class ChatManualTest {

    public static void main(String[] args) throws Exception {

        System.out.println("=== CHAT MANUAL TEST START ===");

        ActorSystem system = ActorSystem.create(
                "chat-service",
                new RabbitTemplate()
        );

        ActorRef chat = system.actorOf(ChatActor.class, "chat");

        system.tell(
                chat,
                Message.of("SEND_MESSAGE", new String[]{"Alice", "Salut !"}),
                null
        );

        system.tell(
                chat,
                Message.of("SEND_MESSAGE", new String[]{"Bob", "Yo 👋"}),
                null
        );

        Thread.sleep(300);

        system.tell(
                chat,
                Message.of("GET_LAST_MESSAGES", 10),
                null
        );

        Thread.sleep(300);

        system.close();
        System.out.println("=== CHAT MANUAL TEST END ===");
    }
}
