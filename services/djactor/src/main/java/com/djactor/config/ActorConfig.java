package com.djactor.config;

import com.framework.actors.ActorSystem;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActorConfig {

    @Bean(destroyMethod = "close")
    public ActorSystem actorSystem(RabbitTemplate rabbitTemplate) {
        return ActorSystem.create("djactor", rabbitTemplate);
    }

    @Bean
    public DJActorFactory djActorFactory(ActorSystem actorSystem) {
        return new DJActorFactory(actorSystem);
    }
}
