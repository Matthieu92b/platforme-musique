package com.chatactor.config;


import com.chatactor.actors.ChatManagerActor;
import com.framework.actors.ActorSystem;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActorConfig {

    @Bean(destroyMethod = "close")
    public ActorSystem actorSystem(RabbitTemplate rabbitTemplate) {
        ActorSystem system = ActorSystem.create("chat-actor", rabbitTemplate);

        // ✅ manager unique
        system.actorOf(ChatManagerActor.class, "chat-manager");

        return system;
    }
}
