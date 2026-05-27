package com.practice.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Valuation data for a single stock holding inside a user's portfolio.
 * Combines the stored buying-price data with the live/cached market price.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingValuation {

    private String symbol;
    private String displaySymbol;
    private String companyName;
    private Integer quantity;

    // ── Stored data (from portfolio table) ───────────────────────────────────
    private BigDecimal buyingPrice;      // price paid per share
    private BigDecimal investmentValue;  // quantity × buyingPrice

    // ── Live / cached market data (from StockService cache) ──────────────────
    private BigDecimal currentPrice;     // latest known market price per share
    private BigDecimal currentValue;     // quantity × currentPrice

    // ── P&L ─────────────────────────────────────────────────────────────────
    private BigDecimal profitLoss;       // currentValue − investmentValue
    private double     plPercent;        // (profitLoss / investmentValue) × 100
    private boolean    gain;             // true when profitLoss ≥ 0

    private String marketState;          // "REGULAR", "CLOSED", "UNKNOWN", …
}
