package com.practice.demo.config;

import com.practice.demo.dto.StockPriceMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka infrastructure configuration.
 *
 * <h3>Topic</h3>
 * <pre>
 *   stock.prices — one message per Nifty 50 quote, published after each
 *                  Yahoo Finance refresh cycle (every 30 s).
 *                  Key = stock symbol (e.g. "RELIANCE.NS")
 *                  Value = {@link StockPriceMessage} JSON
 * </pre>
 *
 * <h3>Producer</h3>
 * {@link StockPriceKafkaProducer} uses the {@link KafkaTemplate} bean.
 * Messages are serialized to JSON via {@link JsonSerializer}.
 * Type-info headers are suppressed ({@code ADD_TYPE_INFO_HEADERS = false})
 * since the consumer is configured with a fixed target type.
 *
 * <h3>Consumer (batch listener)</h3>
 * {@link StockPriceKafkaConsumer} uses {@code batchKafkaListenerContainerFactory}.
 * One Kafka poll delivers an entire batch of messages (all stocks from one refresh
 * cycle), so the consumer fires exactly ONE {@link com.practice.demo.event.StockPricesUpdatedEvent}
 * per refresh — preventing redundant SSE pushes.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${kafka.topic.stock-prices}")
    private String stockPricesTopic;

    @Value("${kafka.topic.partitions:3}")
    private int partitions;

    @Value("${kafka.topic.replication-factor:1}")
    private short replicationFactor;

    // =========================================================================
    // Topic auto-creation (Kafka broker creates the topic on first use)
    // =========================================================================

    @Bean
    public org.apache.kafka.clients.admin.NewTopic stockPricesTopic() {
        return TopicBuilder.name(stockPricesTopic)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    // =========================================================================
    // Producer
    // =========================================================================

    @Bean
    public ProducerFactory<String, StockPriceMessage> stockPriceProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Suppress __TypeId__ headers — consumer uses a fixed target type
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        // Producer acks = 1: leader acknowledgment only (sufficient for internal telemetry)
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, StockPriceMessage> kafkaTemplate(
            ProducerFactory<String, StockPriceMessage> stockPriceProducerFactory) {
        return new KafkaTemplate<>(stockPriceProducerFactory);
    }

    // =========================================================================
    // Consumer
    // =========================================================================

    @Bean
    public ConsumerFactory<String, StockPriceMessage> stockPriceConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // Fixed target type — independent of any producer-set type headers
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, StockPriceMessage.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.practice.demo.dto");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Batch-mode listener factory.
     *
     * <p>The consumer's {@code @KafkaListener} receives a {@code List<StockPriceMessage>}
     * containing all messages available in one Kafka poll.  Because the producer
     * publishes all 50 Nifty 50 quotes in rapid succession after each refresh, a
     * single poll typically delivers them all at once — enabling one atomic
     * LivePriceStore update and one SSE push per 30-second cycle.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, StockPriceMessage>
            batchKafkaListenerContainerFactory(
                ConsumerFactory<String, StockPriceMessage> stockPriceConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, StockPriceMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(stockPriceConsumerFactory);
        factory.setBatchListener(true);   // ← enables List<StockPriceMessage> signature
        return factory;
    }
}
