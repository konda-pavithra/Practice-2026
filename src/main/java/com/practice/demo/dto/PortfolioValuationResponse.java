package com.practice.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full portfolio valuation returned by {@code GET /api/portfolio/valuation}.
 *
 * Combines each holding's buying-price data with the live/cached market
 * price from the stock ticker cache to produce a complete P&amp;L picture.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioValuationResponse {

    private List<HoldingValuation> holdings;
    private int                    totalHoldings;

    // ── Portfolio-level aggregates ───────────────────────────────────────────
    private BigDecimal totalInvestment;    // Σ (qty × buyingPrice)
    private BigDecimal totalCurrentValue;  // Σ (qty × currentPrice)
    private BigDecimal totalProfitLoss;    // totalCurrentValue − totalInvestment
    private double     totalPLPercent;     // (totalProfitLoss / totalInvestment) × 100

    /**
     * Reflects the quality of the market-price data used:
     * "LIVE"        — prices from the latest Yahoo Finance fetch
     * "CACHED"      — prices from a previous fetch (Yahoo Finance was unavailable)
     * "UNAVAILABLE" — no market price data yet; P&amp;L figures will show ₹0
     */
    private String        dataStatus;
    private LocalDateTime valuedAt;
}
