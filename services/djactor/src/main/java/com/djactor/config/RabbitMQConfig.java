package com.djactor.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public Queue addPlayerStateQueue() {
        return new Queue("add-player-state", true);
    }

    @Bean
    public Queue removePlayerStateQueue() {
        return new Queue("remove-player-state", true);
    }

    @Bean
    public Queue togglePlayPauseQueue() {
        return new Queue("toggle-play-pause", true);
    }

    @Bean
    public Queue nextSongQueue() {
        return new Queue("next-song", true);
    }

    @Bean
    public Queue prevSongQueue() {
        return new Queue("prev-song", true);
    }
}