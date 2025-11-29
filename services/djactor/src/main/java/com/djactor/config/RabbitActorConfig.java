package com.djactor.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitActorConfig {

    private static final String EXCHANGE_NAME = "actor.exchange";

    @Bean
    public TopicExchange actorExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue djactorQueue() {
        // doit correspondre à serviceName + ".messages" dans RemoteDispatcher
        return new Queue("djactor.messages", true);
    }

    @Bean
    public Binding djactorBinding(Queue djactorQueue, TopicExchange actorExchange) {
        return BindingBuilder
                .bind(djactorQueue)
                .to(actorExchange)
                .with("djactor.*"); // idem que routingKey prefix dans RemoteDispatcher
    }
}
