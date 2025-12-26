package com.djroom.config;

import com.framework.actors.ActorSystem;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActorsConfig {

    @Bean(destroyMethod = "close")
    public ActorSystem actorSystem(RabbitTemplate rabbitTemplate) {
        return ActorSystem.create("djroom", rabbitTemplate);
    }
}
