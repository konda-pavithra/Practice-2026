package com.practice.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Real-time valuation for a single portfolio holding.
 *
 * <p>Combines three data sources:
 * <ul>
 *   <li><b>Portfolio table</b> — quantity and buying price (stored when the user added the holding)</li>
 *   <li><b>LivePriceStore</b> — current market price received via the Kafka pipeline</li>
 *   <li><b>StockThreshold table</b> — the user's configured upper/lower alert percentages</li>
 * </ul>
 *
 * <p>Computed by {@link com.practice.demo.service.PortfolioRealtimeService} using the
 * Java Streams API and pushed to the client as part of an SSE event every 30 s.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingRealtimeValuation {

    private String  symbol;
    private String  displaySymbol;
    private String  companyName;
    private Integer quantity;

    // ── Stored portfolio data ────────────────────────────────────────────────
    private BigDecimal buyingPrice;       // average buy price per share
    private BigDecimal investmentValue;   // quantity × buyingPrice

    // ── Live market data (from LivePriceStore / Kafka pipeline) ─────────────
    private BigDecimal currentPrice;      // latest price from Kafka
    private BigDecimal currentValue;      // quantity × currentPrice
    private double     dayChangePercent;  // intra-day % change from previous close
    private String     marketState;       // "REGULAR", "CLOSED", etc.

    // ── Profit & Loss ────────────────────────────────────────────────────────
    private BigDecimal profitLoss;        // currentValue − investmentValue
    private double     plPercent;         // (profitLoss / investmentValue) × 100
    private boolean    gain;              // true when profitLoss ≥ 0

    // ── Threshold status ─────────────────────────────────────────────────────

    /**
     * Describes whether the current price has crossed either threshold level.
     * {@link ThresholdStatus#NO_THRESHOLD} when no threshold is configured.
     */
    private ThresholdStatus thresholdStatus;

    /** Configured upper threshold percentage (e.g. 5.00 = 5%). {@code null} if no threshold. */
    private BigDecimal upperThresholdPercent;

    /** Configured lower threshold percentage (e.g. 3.00 = 3%). {@code null} if no threshold. */
    private BigDecimal lowerThresholdPercent;

    /** Absolute price level that would trigger the upper alert. {@code null} if no threshold. */
    private BigDecimal upperAlertPrice;

    /** Absolute price level that would trigger the lower alert. {@code null} if no threshold. */
    private BigDecimal lowerAlertPrice;
}
