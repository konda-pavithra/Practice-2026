package com.practice.demo.store;

import com.practice.demo.dto.StockPriceMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory store of the latest market prices received through the
 * Kafka pipeline.
 *
 * <h3>Role in the architecture</h3>
 * <pre>
 *  YahooFinanceClient
 *       │ every 30 s
 *       ▼
 *  StockService (own volatile cache — backs REST /api/stocks/quotes)
 *       │
 *  StockPriceKafkaProducer  →  [Kafka: stock.prices]  →  StockPriceKafkaConsumer
 *                                                               │
 *                                                        LivePriceStore  ← here
 *                                                               │
 *                                              PortfolioRealtimeService
 *                                                               │
 *                                                     SSE /api/portfolio/stream
 * </pre>
 *
 * <h3>Thread safety</h3>
 * Backed by {@link ConcurrentHashMap}; individual puts and gets are atomic.
 * The Kafka consumer writes and the SSE service reads concurrently with no risk
 * of partial updates because each {@code update()} call is an atomic map put.
 */
@Component
public class LivePriceStore {

    private static final Logger logger = LoggerFactory.getLogger(LivePriceStore.class);

    /** symbol (e.g. "RELIANCE.NS") → latest price message from Kafka. */
    private final ConcurrentHashMap<String, StockPriceMessage> store = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Stores or replaces the price data for a single symbol.
     * Called by {@link com.practice.demo.consumer.StockPriceKafkaConsumer} for every
     * message in a batch.
     */
    public void update(StockPriceMessage message) {
        store.put(message.getSymbol(), message);
        logger.trace("LivePriceStore updated: {} = ₹{}", message.getSymbol(), message.getPrice());
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Returns the latest price message for {@code symbol}, or empty if no
     * Kafka data has arrived for this symbol yet.
     */
    public Optional<StockPriceMessage> get(String symbol) {
        return Optional.ofNullable(store.get(symbol));
    }

    /**
     * Returns a read-only snapshot of all stored prices.
     * Used by {@link com.practice.demo.service.PortfolioRealtimeService} to compute
     * portfolio valuations with a single map-lookup per holding.
     */
    public Map<String, StockPriceMessage> getAll() {
        return Collections.unmodifiableMap(store);
    }

    /** {@code true} when at least one price has been received from Kafka. */
    public boolean hasData() {
        return !store.isEmpty();
    }

    /** Number of symbols currently in the store. */
    public int size() {
        return store.size();
    }
}
