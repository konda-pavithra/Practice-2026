package com.practice.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Shown to the user in the upload preview when a stock already exists in
 * their portfolio and will be overwritten if they confirm.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioUpdateItem {

    private String symbol;
    private String displaySymbol;
    private String companyName;

    // ── Current values stored in the database ────────────────────────────────
    private Integer    currentQuantity;
    private BigDecimal currentBuyingPrice;

    // ── New values from the uploaded Excel sheet ─────────────────────────────
    private Integer    newQuantity;
    private BigDecimal newBuyingPrice;

    /** Human-readable diff message shown to the user before they confirm. */
    private String changeDescription;
}
