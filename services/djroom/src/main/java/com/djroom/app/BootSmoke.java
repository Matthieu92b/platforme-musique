// src/main/java/com/djroom/app/BootSmoke.java
package com.djroom.app;

import com.djroom.app.actors.Dispatcher;
import com.djroom.app.actors.Message;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootSmoke {
    @Bean
    CommandLineRunner pingEcho(Dispatcher dispatcher) {
        return args -> {
            dispatcher.tell("echo", Message.of("Ping", "Hello actors!"));
            // petit délai pour voir le log
            Thread.sleep(200);
        };
    }
}
