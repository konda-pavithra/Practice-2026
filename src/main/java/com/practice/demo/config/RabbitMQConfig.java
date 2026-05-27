package com.practice.demo.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * RabbitMQ topology and serialization configuration.
 *
 * <h3>Topology</h3>
 * <pre>
 *  [AlertGeneratorScheduler]
 *        │ convertAndSend(EXCHANGE, ROUTING_KEY, message)
 *        ▼
 *  stock.alerts.exchange  (Direct)
 *        │ routing key: stock.alert
 *        ▼
 *  stock.alerts.queue  ──────────────────────────────► [StockAlertConsumer]
 *        │                                                     │ sends email
 *        │  on max-retries exhausted (3 attempts, exponential) │
 *        ▼                                                     │ (re-throws on failure)
 *  stock.alerts.dlx  (Dead-letter exchange)                    │
 *        │                                                     ▼
 *        ▼                                           stock.alerts.dlq
 *  stock.alerts.dlq  ◄─── inspect / replay manually
 * </pre>
 *
 * <h3>Message format</h3>
 * Messages are serialized as JSON via {@link Jackson2JsonMessageConverter}.
 * The {@code __TypeId__} header carries the fully-qualified class name so
 * the consumer can deserialize without extra configuration.
 *
 * <h3>Retry policy</h3>
 * The listener container retries failed message processing up to 3 times
 * (with exponential back-off: 1 s → 2 s → 4 s). After 3 failures the
 * message is rejected and routed to the DLQ for manual inspection.
 */
@Configuration
public class RabbitMQConfig {

    // ── Queue / Exchange / Routing-key names (public for use in other beans) ──

    public static final String ALERT_EXCHANGE    = "stock.alerts.exchange";
    public static final String ALERT_QUEUE       = "stock.alerts.queue";
    public static final String ALERT_ROUTING_KEY = "stock.alert";

    public static final String DLX = "stock.alerts.dlx";
    public static final String DLQ = "stock.alerts.dlq";

    // =========================================================================
    // Dead-letter infrastructure
    // =========================================================================

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(ALERT_ROUTING_KEY);
    }

    // =========================================================================
    // Main alert infrastructure
    // =========================================================================

    @Bean
    public DirectExchange alertExchange() {
        return new DirectExchange(ALERT_EXCHANGE, true, false);
    }

    @Bean
    public Queue alertQueue() {
        return QueueBuilder.durable(ALERT_QUEUE)
                .withArgument("x-dead-letter-exchange",    DLX)
                .withArgument("x-dead-letter-routing-key", ALERT_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding alertBinding(Queue alertQueue, DirectExchange alertExchange) {
        return BindingBuilder.bind(alertQueue)
                .to(alertExchange)
                .with(ALERT_ROUTING_KEY);
    }

    // =========================================================================
    // JSON message converter
    // =========================================================================

    /**
     * Uses Jackson to serialize/deserialize AMQP messages as JSON.
     * Spring Boot auto-wires this bean into the auto-configured {@link RabbitTemplate}.
     */
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Overrides the auto-configured {@link RabbitTemplate} to use the JSON converter
     * so that {@code rabbitTemplate.convertAndSend(..., message)} serializes to JSON.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    // =========================================================================
    // Listener container factory  (retry + JSON deserialization)
    // =========================================================================

    /**
     * Retry interceptor: 3 attempts with 1 s → 2 s → 4 s exponential back-off.
     * After all attempts are exhausted the message is rejected (not re-queued),
     * which causes RabbitMQ to route it to the DLQ via the dead-letter exchange.
     */
    @Bean
    public RetryOperationsInterceptor retryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1_000, 2.0, 4_000)   // initialMs, multiplier, maxMs
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    /**
     * Custom listener container factory that wires in both the JSON message
     * converter and the retry interceptor.  {@link com.practice.demo.consumer.StockAlertConsumer}
     * references this factory by name via {@code containerFactory = "rabbitListenerContainerFactory"}.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jsonMessageConverter,
            RetryOperationsInterceptor retryInterceptor) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(retryInterceptor);
        return factory;
    }
}
