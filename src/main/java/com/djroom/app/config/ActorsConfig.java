// src/main/java/com/djroom/app/config/ActorsConfig.java
package com.djroom.app.config;

import com.djroom.app.actors.Dispatcher;
import com.djroom.app.actors.EchoActor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActorsConfig {
    @Bean(destroyMethod = "close")
    public Dispatcher dispatcher() {
        var d = new Dispatcher();
        d.register("echo", new EchoActor()); // acteur de test
        return d;
    }
}
