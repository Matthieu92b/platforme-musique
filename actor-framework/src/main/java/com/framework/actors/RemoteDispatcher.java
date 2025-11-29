package com.framework.actors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles remote communication via RabbitMQ.
 */
public class RemoteDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RemoteDispatcher.class);
    private static final String EXCHANGE_NAME = "actor.exchange";

    private final String serviceName;
    private final RabbitTemplate rabbitTemplate;
    private final ConnectionFactory connectionFactory;
    private final LocalDispatcher localDispatcher;
    private final ObjectMapper objectMapper;
    private SimpleMessageListenerContainer listenerContainer;

    public RemoteDispatcher(String serviceName,
                            RabbitTemplate rabbitTemplate,
                            ConnectionFactory connectionFactory,
                            LocalDispatcher localDispatcher) {
        this.serviceName = serviceName;
        this.rabbitTemplate = rabbitTemplate;
        this.connectionFactory = connectionFactory;
        this.localDispatcher = localDispatcher;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // ⚠️ PLUS BESOIN DE CONFIGURER LE CONVERTER ICI
        // Il est maintenant configuré dans RabbitMQConfig.rabbitTemplate()

        // Setup RabbitMQ infrastructure
        setupRabbitMQ();

        // Start listening
        startListening();
    }

    /**
     * Setup RabbitMQ exchange, queue, and binding.
     */
    private void setupRabbitMQ() {
        try {
            RabbitAdmin admin = new RabbitAdmin(connectionFactory);

            // Create exchange
            TopicExchange exchange = new TopicExchange(EXCHANGE_NAME, true, false);
            admin.declareExchange(exchange);
            log.info("📡 Exchange declared: {}", EXCHANGE_NAME);

            // Create queue
            String queueName = serviceName + ".messages";
            Queue queue = new Queue(queueName, true);
            admin.declareQueue(queue);
            log.info("📥 Queue declared: {}", queueName);

            // Create binding
            Binding binding = BindingBuilder
                    .bind(queue)
                    .to(exchange)
                    .with(serviceName + ".*");
            admin.declareBinding(binding);
            log.info("🔗 Binding created: {} -> {} (pattern: {}.*)",
                    queueName, EXCHANGE_NAME, serviceName);

        } catch (Exception e) {
            log.error("❌ Failed to setup RabbitMQ", e);
            throw new RuntimeException("RabbitMQ setup failed", e);
        }
    }

    /**
     * Send message to remote actor.
     */
    public void send(String targetPath, Message message, ActorRef sender) {
        try {
            String[] parts = targetPath.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid path: " + targetPath);
            }

            String targetService = parts[0];
            String actorName = parts[1];
            String routingKey = targetService + "." + actorName;

            // On crée un Map simple -> JSON
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("messageType", message.type());
            envelope.put("payload", message.payload());
            envelope.put("correlationId", message.correlationId());
            envelope.put("senderPath", sender != null ? sender.path() : null);
            envelope.put("targetPath", targetPath);

            log.info("📤 [{}] Sending {} to {} (routing: {})",
                    serviceName, message.type(), targetPath, routingKey);

            // 🔥 ICI LE CHANGEMENT IMPORTANT :
            // on sérialise nous-mêmes avec objectMapper
            byte[] body = objectMapper.writeValueAsBytes(envelope);

            org.springframework.amqp.core.Message amqpMsg =
                    MessageBuilder.withBody(body)
                            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                            .build();

            // Et on utilise send() (PAS convertAndSend)
            rabbitTemplate.send(EXCHANGE_NAME, routingKey, amqpMsg);

            log.debug("✅ Message sent successfully");

        } catch (Exception e) {
            log.error("❌ Failed to send remote message", e);
            throw new RuntimeException("Remote send failed", e);
        }
    }

    /**
     * Start listening for incoming messages.
     */
    private void startListening() {
        try {
            String queueName = serviceName + ".messages";

            listenerContainer = new SimpleMessageListenerContainer(connectionFactory);
            listenerContainer.setQueueNames(queueName);

            // ❌ LIGNE SUPPRIMÉE : pas de setMessageConverter ici
            // listenerContainer.setMessageConverter(new Jackson2JsonMessageConverter());

            listenerContainer.setMessageListener(message -> {
                try {
                    byte[] body = message.getBody();
                    String json = new String(body);

                    log.debug("📨 [{}] Raw message received: {}", serviceName, json);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> envelope = objectMapper.readValue(json, Map.class);

                    String messageType = (String) envelope.get("messageType");
                    Object payload = envelope.get("payload");
                    String correlationId = (String) envelope.get("correlationId");
                    String senderPath = (String) envelope.get("senderPath");
                    String targetPath = (String) envelope.get("targetPath");

                    log.info("📥 [{}] Received {} for {}", serviceName, messageType, targetPath);

                    // Reconstruct message
                    Message msg = new Message(messageType, payload, correlationId, senderPath);

                    // Reconstruct sender
                    ActorRef sender = senderPath != null
                            ? new RemoteActorRef(senderPath, null)
                            : null;

                    // Dispatch locally
                    log.debug("🔄 Routing to local actor: {}", targetPath);
                    localDispatcher.dispatch(targetPath, msg, sender);

                    log.debug("✅ Message delivered to local actor");

                } catch (Exception e) {
                    log.error("❌ Failed to process incoming message", e);
                }
            });

            listenerContainer.start();

            log.info("👂 [{}] Listening on queue: {}", serviceName, queueName);

        } catch (Exception e) {
            log.error("❌ Failed to start listener", e);
            throw new RuntimeException("Failed to start listener", e);
        }
    }

    public void shutdown() {
        if (listenerContainer != null && listenerContainer.isRunning()) {
            listenerContainer.stop();
            log.info("🔴 Listener stopped for: {}", serviceName);
        }
    }
}
