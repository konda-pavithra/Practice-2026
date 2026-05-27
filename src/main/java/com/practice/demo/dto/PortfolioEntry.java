package com.practice.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Represents a single validated stock entry.
 *
 * Flow:
 *  - After Excel parsing  : {@code symbol} holds the raw string from the sheet.
 *  - After service validation : {@code symbol} is normalized to Yahoo Finance
 *    format (e.g. "RELIANCE.NS"), and {@code displaySymbol} / {@code companyName}
 *    are populated.
 *  - In {@link PortfolioConfirmRequest}: fully normalized, ready for persistence.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioEntry {

    private String     symbol;        // Yahoo Finance symbol: "RELIANCE.NS"
    private String     displaySymbol; // Exchange-only ticker:  "RELIANCE"
    private String     companyName;
    private Integer    quantity;
    private BigDecimal buyingPrice;
}
