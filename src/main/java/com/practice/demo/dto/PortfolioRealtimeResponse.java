package com.practice.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Complete real-time portfolio snapshot delivered via SSE to the browser.
 *
 * <p>Pushed once per Kafka batch event (every 30 s) by
 * {@link com.practice.demo.service.PortfolioRealtimeService}.
 *
 * <p>Contains:
 * <ul>
 *   <li>Per-holding valuations with P&amp;L and threshold status</li>
 *   <li>Portfolio-level aggregates (total investment, current value, overall P&amp;L)</li>
 *   <li>Threshold breach summary counts</li>
 *   <li>Data quality indicator ({@code dataStatus})</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioRealtimeResponse {

    // ── Individual holdings ──────────────────────────────────────────────────
    private List<HoldingRealtimeValuation> holdings;
    private int totalHoldings;

    // ── Portfolio-level aggregates ───────────────────────────────────────────
    private BigDecimal totalInvestment;    // Σ (qty × buyingPrice)
    private BigDecimal totalCurrentValue;  // Σ (qty × currentPrice)
    private BigDecimal totalProfitLoss;    // totalCurrentValue − totalInvestment
    private double     totalPLPercent;     // (totalProfitLoss / totalInvestment) × 100

    // ── Threshold breach summary ─────────────────────────────────────────────
    /** Holdings whose current price is ≥ their configured upper alert price. */
    private int holdingsAboveUpperThreshold;

    /** Holdings whose current price is ≤ their configured lower alert price. */
    private int holdingsBelowLowerThreshold;

    /** Holdings with a threshold configured but currently within bounds. */
    private int holdingsWithinBounds;

    /** Holdings that have no threshold configured. */
    private int holdingsWithoutThreshold;

    // ── Metadata ─────────────────────────────────────────────────────────────

    /**
     * Quality of the price data used for this valuation.
     * <ul>
     *   <li>{@code "LIVE"}        — prices from the latest Kafka batch (Yahoo Finance fresh data)</li>
     *   <li>{@code "CACHED"}      — Kafka store empty; fell back to StockService REST cache</li>
     *   <li>{@code "UNAVAILABLE"} — no price data from either source; P&amp;L shows ₹0</li>
     * </ul>
     */
    private String dataStatus;

    /** Timestamp when this response was computed on the server. */
    private LocalDateTime valuedAt;
}
