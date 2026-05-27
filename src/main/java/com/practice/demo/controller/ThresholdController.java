package com.practice.demo.controller;

import com.practice.demo.dto.ThresholdRequest;
import com.practice.demo.dto.ThresholdResponse;
import com.practice.demo.service.ThresholdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Stock price threshold management — all endpoints require a valid JWT.
 *
 * ┌──────────────────────────────────────┬──────────────────────────────────────────────────┐
 * │ Endpoint                             │ Purpose                                          │
 * ├──────────────────────────────────────┼──────────────────────────────────────────────────┤
 * │ PUT    /api/thresholds/{symbol}      │ Set or update thresholds for a stock (upsert)    │
 * │ GET    /api/thresholds               │ List all thresholds configured by the user        │
 * │ GET    /api/thresholds/{symbol}      │ Get thresholds for one specific stock             │
 * │ DELETE /api/thresholds/{symbol}      │ Remove thresholds for a stock                    │
 * └──────────────────────────────────────┴──────────────────────────────────────────────────┘
 *
 * Threshold semantics:
 *   upperThresholdPercent — positive %; alert fires when price ≥ referencePrice × (1 + upper/100)
 *   lowerThresholdPercent — positive %; alert fires when price ≤ referencePrice × (1 − lower/100)
 *   referencePrice        — market price snapshotted at the moment of last save
 *   upperAlertPrice       — pre-computed absolute upper trigger level (₹)
 *   lowerAlertPrice       — pre-computed absolute lower trigger level (₹)
 *
 * Symbol resolution: accepts bare ticker ("RELIANCE"), qualified form ("RELIANCE.NS"),
 * or company display name ("Reliance Industries Limited") in the path variable.
 */
@RestController
@RequestMapping("/api/thresholds")
public class ThresholdController {

    private static final Logger logger = LoggerFactory.getLogger(ThresholdController.class);

    private final ThresholdService thresholdService;

    public ThresholdController(ThresholdService thresholdService) {
        this.thresholdService = thresholdService;
    }

    // =========================================================================
    // Set / change threshold (upsert)
    // =========================================================================

    /**
     * Sets or updates the upper and lower price thresholds for a stock.
     *
     * <p>This is an <em>upsert</em> — calling it the first time for a symbol
     * creates the threshold; calling it again updates the percentages and
     * refreshes the reference price from the current market cache.
     *
     * <p>Request body:
     * <pre>
     * {
     *   "upperThresholdPercent": 5.0,
     *   "lowerThresholdPercent": 3.0
     * }
     * </pre>
     *
     * <p>Responses:
     * <ul>
     *   <li>200 OK          — threshold saved; body is {@link ThresholdResponse} with
     *                         referencePrice and pre-computed alert prices.</li>
     *   <li>400 Bad Request — invalid symbol or non-positive percentage value.</li>
     * </ul>
     */
    @PutMapping("/{symbol}")
    public ResponseEntity<ThresholdResponse> setThreshold(
            @PathVariable String symbol,
            @RequestBody ThresholdRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("PUT /api/thresholds/{} — user '{}': upper={}%, lower={}%",
                symbol, username,
                request.getUpperThresholdPercent(), request.getLowerThresholdPercent());

        ThresholdResponse response = thresholdService.setThreshold(symbol, request, username);

        logger.info("PUT /api/thresholds/{} — user '{}': saved — refPrice={}, upperAlert=₹{}, lowerAlert=₹{}",
                symbol, username,
                response.getReferencePrice(),
                response.getUpperAlertPrice(),
                response.getLowerAlertPrice());

        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Read all thresholds
    // =========================================================================

    /**
     * Returns all threshold configurations for the authenticated user,
     * sorted alphabetically by symbol.
     *
     * <p>Returns an empty list if no thresholds have been set yet.
     */
    @GetMapping
    public ResponseEntity<List<ThresholdResponse>> getAllThresholds(Authentication authentication) {
        String username = authentication.getName();
        logger.info("GET /api/thresholds — user '{}'", username);

        List<ThresholdResponse> list = thresholdService.getAllThresholds(username);

        logger.info("GET /api/thresholds — user '{}': {} threshold(s) returned", username, list.size());
        return ResponseEntity.ok(list);
    }

    // =========================================================================
    // Read single threshold
    // =========================================================================

    /**
     * Returns the threshold configuration for a single stock.
     *
     * <p>Responses:
     * <ul>
     *   <li>200 OK          — {@link ThresholdResponse}.</li>
     *   <li>400 Bad Request — invalid symbol, or no threshold has been set for that stock.</li>
     * </ul>
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<ThresholdResponse> getThreshold(
            @PathVariable String symbol,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("GET /api/thresholds/{} — user '{}'", symbol, username);

        ThresholdResponse response = thresholdService.getThreshold(symbol, username);

        logger.info("GET /api/thresholds/{} — user '{}': upper={}%, lower={}%, refPrice={}",
                symbol, username,
                response.getUpperThresholdPercent(),
                response.getLowerThresholdPercent(),
                response.getReferencePrice());

        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Delete threshold
    // =========================================================================

    /**
     * Removes the threshold configuration for a stock.
     *
     * <p>Responses:
     * <ul>
     *   <li>204 No Content  — threshold deleted.</li>
     *   <li>400 Bad Request — invalid symbol or no threshold was set for that stock.</li>
     * </ul>
     */
    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> deleteThreshold(
            @PathVariable String symbol,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("DELETE /api/thresholds/{} — user '{}'", symbol, username);

        thresholdService.deleteThreshold(symbol, username);

        logger.info("DELETE /api/thresholds/{} — user '{}': threshold removed", symbol, username);
        return ResponseEntity.noContent().build();
    }
}
