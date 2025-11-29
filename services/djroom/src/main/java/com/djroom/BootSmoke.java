package com.djroom;

import com.framework.actors.ActorRef;
import com.framework.actors.ActorSystem;
import com.framework.actors.Message;
import com.djroom.actors.EchoActor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootSmoke {

    @Bean
    CommandLineRunner pingEcho(ActorSystem actorSystem) {
        return args -> {
            // Create echo actor using the framework
            ActorRef echo = actorSystem.actorOf(EchoActor.class, "echo");

            // Send test message
            Message msg = Message.of("PING", "Hello from framework!");
            echo.tell(msg, null);

            // Wait to see the log
            Thread.sleep(500);
        };
    }
}