package com.practice.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single Nifty 50 stock entry returned by {@code GET /api/portfolio/stocks}.
 * Used to populate the stock-name dropdown / autocomplete in the UI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NseStockInfo {

    private String symbol;        // Yahoo Finance symbol: "RELIANCE.NS"
    private String displaySymbol; // Exchange-only ticker:  "RELIANCE"
    private String companyName;   // Full name:             "Reliance Industries"
}
