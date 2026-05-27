package com.practice.demo.consumer;

import com.practice.demo.dto.StockPriceMessage;
import com.practice.demo.event.StockPricesUpdatedEvent;
import com.practice.demo.store.LivePriceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka consumer — the receiving end of the {@code stock.prices} pipeline.
 *
 * <h3>Batch listener</h3>
 * Configured as a batch listener (via {@code batchKafkaListenerContainerFactory}).
 * A single invocation of {@link #consumePriceBatch(List)} receives ALL messages
 * available in one Kafka poll, which corresponds to all 50 Nifty 50 quotes
 * published by {@link com.practice.demo.producer.StockPriceKafkaProducer} after
 * each Yahoo Finance refresh cycle.
 *
 * <h3>Processing</h3>
 * <ol>
 *   <li>Each message in the batch updates {@link LivePriceStore} (one ConcurrentHashMap
 *       put per symbol — thread-safe, O(1)).</li>
 *   <li>After all messages are stored, ONE {@link StockPricesUpdatedEvent} is
 *       published via Spring's {@link ApplicationEventPublisher}.</li>
 *   <li>{@link com.practice.demo.service.PortfolioRealtimeService} listens to this
 *       event and pushes fresh portfolio valuations to all connected SSE clients.</li>
 * </ol>
 *
 * <h3>Why one event per batch (not per message)?</h3>
 * Publishing 50 individual events would trigger 50 SSE pushes per cycle for each
 * connected user.  Batching collapses those into a single push after the store
 * is fully up-to-date, giving every client a consistent, complete snapshot.
 */
@Component
public class StockPriceKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(StockPriceKafkaConsumer.class);

    private final LivePriceStore           livePriceStore;
    private final ApplicationEventPublisher eventPublisher;

    public StockPriceKafkaConsumer(LivePriceStore livePriceStore,
                                   ApplicationEventPublisher eventPublisher) {
        this.livePriceStore  = livePriceStore;
        this.eventPublisher  = eventPublisher;
    }

    /**
     * Receives a batch of {@link StockPriceMessage} records from the
     * {@code stock.prices} Kafka topic, updates the live price store, and
     * publishes a single application event so SSE clients are notified.
     *
     * @param messages all records available in the current Kafka poll cycle
     */
    @KafkaListener(
        topics           = "${kafka.topic.stock-prices}",
        containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void consumePriceBatch(List<StockPriceMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        logger.debug("KafkaConsumer — received batch of {} price message(s)", messages.size());

        // ── 1. Update LivePriceStore ──────────────────────────────────────────
        messages.forEach(livePriceStore::update);

        logger.info("KafkaConsumer — LivePriceStore updated: {} symbol(s), store size={}",
                messages.size(), livePriceStore.size());

        // ── 2. Fire ONE application event for the whole batch ─────────────────
        //    PortfolioRealtimeService will push SSE events to all connected clients.
        eventPublisher.publishEvent(new StockPricesUpdatedEvent(this, messages));

        logger.debug("KafkaConsumer — StockPricesUpdatedEvent published");
    }
}
