package com.practice.demo.event;

import com.practice.demo.dto.StockPriceMessage;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * Spring application event fired by {@link com.practice.demo.consumer.StockPriceKafkaConsumer}
 * after an entire batch of Nifty 50 price messages has been consumed from Kafka and
 * the {@link com.practice.demo.store.LivePriceStore} has been fully updated.
 *
 * <p>Exactly ONE event is fired per Yahoo Finance refresh cycle (every 30 s), regardless
 * of how many individual Kafka messages were in the batch.  This prevents redundant SSE
 * pushes to connected clients.
 *
 * <p>The event payload carries the raw price messages so listeners can inspect them
 * without reading from the store again, though reading from
 * {@link com.practice.demo.store.LivePriceStore} is equally valid.
 */
public class StockPricesUpdatedEvent extends ApplicationEvent {

    private final List<StockPriceMessage> updatedQuotes;

    public StockPricesUpdatedEvent(Object source, List<StockPriceMessage> updatedQuotes) {
        super(source);
        this.updatedQuotes = List.copyOf(updatedQuotes); // defensive copy
    }

    /** The batch of price messages that triggered this event. */
    public List<StockPriceMessage> getUpdatedQuotes() {
        return updatedQuotes;
    }
}
