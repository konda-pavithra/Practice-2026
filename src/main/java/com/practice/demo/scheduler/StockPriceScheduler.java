package com.practice.demo.scheduler;

import com.practice.demo.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives periodic refresh of the Nifty 50 stock price cache.
 *
 * fixedDelay  — next run starts N ms after the previous run completes,
 *               so a slow Yahoo Finance call never causes overlapping fetches.
 * initialDelay — small pause at startup so the application context is fully
 *               ready before the first network call is made.
 *
 * Both values are configurable via application.properties:
 *   stock.refresh.interval-ms     (default 30 000 ms = 30 s)
 *   stock.refresh.initial-delay-ms (default  5 000 ms =  5 s)
 */
@Component
public class StockPriceScheduler {

    private static final Logger logger = LoggerFactory.getLogger(StockPriceScheduler.class);

    private final StockService stockService;

    public StockPriceScheduler(StockService stockService) {
        this.stockService = stockService;
    }

    @Scheduled(
        fixedDelayString   = "${stock.refresh.interval-ms:30000}",
        initialDelayString = "${stock.refresh.initial-delay-ms:5000}"
    )
    public void refreshStockPrices() {
        logger.info("Scheduler — triggering Nifty 50 price refresh");
        stockService.refreshQuotes();
        logger.info("Scheduler — price refresh cycle complete");
    }
}
