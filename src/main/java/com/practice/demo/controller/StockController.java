package com.practice.demo.controller;

import com.practice.demo.dto.StockTickerResponse;
import com.practice.demo.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the Nifty 50 stock ticker API for authenticated users.
 *
 *   GET /api/stocks/quotes  — returns a JSON snapshot of all 50 stock prices.
 *
 * The UI polls this endpoint at a fixed interval (e.g. every 30 seconds)
 * to keep the ticker bar current. Each call is independently authenticated
 * via the JWT in the Authorization header.
 *
 * The server-side {@link com.practice.demo.scheduler.StockPriceScheduler}
 * refreshes the in-memory cache from Yahoo Finance every 30 seconds, so
 * every poll call receives up-to-date data with no extra Yahoo Finance cost.
 */
@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(origins = "*")
public class StockController {

    private static final Logger logger = LoggerFactory.getLogger(StockController.class);

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    /**
     * Returns the current Nifty 50 quote snapshot from the in-memory cache.
     *
     * <p>Authentication: JWT required ({@code Authorization: Bearer <token>}).</p>
     *
     * <p>Response {@code dataStatus} field:
     * <ul>
     *   <li>{@code "LIVE"}        — freshly fetched from Yahoo Finance this cycle</li>
     *   <li>{@code "CACHED"}      — Yahoo Finance had a transient error; last good data served</li>
     *   <li>{@code "UNAVAILABLE"} — application just started, first fetch pending</li>
     * </ul>
     * </p>
     */
    @GetMapping("/quotes")
    public ResponseEntity<StockTickerResponse> getQuotes() {
        logger.info("GET /api/stocks/quotes — serving Nifty 50 snapshot");
        StockTickerResponse response = stockService.getCurrentTickerResponse();
        logger.debug("GET /api/stocks/quotes — {} quotes returned, dataStatus={}",
                response.getCount(), response.getDataStatus());
        return ResponseEntity.ok(response);
    }
}
