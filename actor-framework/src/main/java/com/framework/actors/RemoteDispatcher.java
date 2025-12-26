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
 * Dispatcher distant :
 * - envoie des messages vers d'autres services via RabbitMQ
 * - écoute la queue du service courant et redispatche localement
 */
public class RemoteDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RemoteDispatcher.class);
    private static final String EXCHANGE_NAME = "actor.exchange";

    private final String serviceName;
    private final RabbitTemplate rabbitTemplate;
    private final ConnectionFactory connectionFactory;
    private final LocalDispatcher localDispatcher;
    private final ActorSystem system;

    // Sérialisation/désérialisation manuelle des enveloppes
    private final ObjectMapper objectMapper;

    private SimpleMessageListenerContainer listenerContainer;

    public RemoteDispatcher(
            String serviceName,
            RabbitTemplate rabbitTemplate,
            ConnectionFactory connectionFactory,
            LocalDispatcher localDispatcher,
            ActorSystem system
    ) {
        this.serviceName = serviceName;
        this.rabbitTemplate = rabbitTemplate;
        this.connectionFactory = connectionFactory;
        this.localDispatcher = localDispatcher;
        this.system = system;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // L'infrastructure RabbitMQ (exchange/queue/binding) est créée au démarrage
        setupRabbitMQ();

        // Démarre l'écoute des messages entrants
        startListening();
    }

    /**
     * Déclare l'exchange, la queue du service, et le binding associé.
     */
    private void setupRabbitMQ() {
        try {
            RabbitAdmin admin = new RabbitAdmin(connectionFactory);

            TopicExchange exchange = new TopicExchange(EXCHANGE_NAME, true, false);
            admin.declareExchange(exchange);
            log.info("Exchange declared: {}", EXCHANGE_NAME);

            String queueName = serviceName + ".messages";
            Queue queue = new Queue(queueName, true);
            admin.declareQueue(queue);
            log.info("Queue declared: {}", queueName);

            Binding binding = BindingBuilder
                    .bind(queue)
                    .to(exchange)
                    .with(serviceName + ".*");
            admin.declareBinding(binding);

            log.info("Binding created: queue={} exchange={} pattern={}",
                    queueName, EXCHANGE_NAME, serviceName + ".*");

        } catch (Exception e) {
            log.error("Failed to setup RabbitMQ for service {}", serviceName, e);
            throw new RuntimeException("RabbitMQ setup failed", e);
        }
    }

    /**
     * Envoie un message vers un acteur distant.
     * Le path attendu est de la forme : "<service>/<actorName>".
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

            // Enveloppe simple sérialisée en JSON
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("messageType", message.type());
            envelope.put("payload", message.payload());
            envelope.put("correlationId", message.correlationId());
            envelope.put("senderPath", sender != null ? sender.path() : null);
            envelope.put("targetPath", targetPath);

            log.info("[{}] Sending message type={} to {} (routingKey={})",
                    serviceName, message.type(), targetPath, routingKey);

            // Sérialisation manuelle (on n'utilise pas convertAndSend)
            byte[] body = objectMapper.writeValueAsBytes(envelope);

            org.springframework.amqp.core.Message amqpMsg = MessageBuilder.withBody(body)
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .build();

            rabbitTemplate.send(EXCHANGE_NAME, routingKey, amqpMsg);

            log.debug("Remote message sent (type={}, target={})", message.type(), targetPath);

        } catch (Exception e) {
            log.error("Failed to send remote message to {}", targetPath, e);
            throw new RuntimeException("Remote send failed", e);
        }
    }

    /**
     * Démarre l'écoute de la queue "<service>.messages" et redispatche vers le LocalDispatcher.
     */
    private void startListening() {
        try {
            String queueName = serviceName + ".messages";

            listenerContainer = new SimpleMessageListenerContainer(connectionFactory);
            listenerContainer.setQueueNames(queueName);

            // La conversion est gérée manuellement via objectMapper (pas de messageConverter)
            listenerContainer.setMessageListener(message -> {
                try {
                    byte[] body = message.getBody();
                    String json = new String(body);

                    log.debug("[{}] Raw message received: {}", serviceName, json);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> envelope = objectMapper.readValue(json, Map.class);

                    String messageType = (String) envelope.get("messageType");
                    Object payload = envelope.get("payload");
                    String correlationId = (String) envelope.get("correlationId");
                    String senderPath = (String) envelope.get("senderPath");
                    String targetPath = (String) envelope.get("targetPath");

                    log.info("[{}] Received message type={} for {}",
                            serviceName, messageType, targetPath);

                    // Reconstruction du message framework
                    Message msg = new Message(messageType, payload, correlationId, senderPath);

                    // Reconstruction du sender (référence distante)
                    ActorRef sender = (senderPath != null)
                            ? new RemoteActorRef(senderPath, system)
                            : null;

                    // Dispatch local vers l'acteur ciblé
                    localDispatcher.dispatch(targetPath, msg, sender);

                } catch (Exception e) {
                    log.error("Failed to process incoming message for service {}", serviceName, e);
                }
            });

            listenerContainer.start();

            log.info("[{}] Listening on queue {}", serviceName, queueName);

        } catch (Exception e) {
            log.error("Failed to start listener for service {}", serviceName, e);
            throw new RuntimeException("Failed to start listener", e);
        }
    }

    /**
     * Arrêt propre du listener RabbitMQ.
     */
    public void shutdown() {
        if (listenerContainer != null && listenerContainer.isRunning()) {
            listenerContainer.stop();
            log.info("[{}] Listener stopped", serviceName);
        }
    }
}
