package com.practice.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response for a single stock price threshold configuration.
 *
 * <p>In addition to the stored upper/lower percentages the response includes
 * two <em>pre-computed</em> absolute price levels — {@code upperAlertPrice} and
 * {@code lowerAlertPrice} — so the UI can display exactly which rupee values
 * would trigger each alert without needing client-side arithmetic.
 *
 * <pre>
 * upperAlertPrice = referencePrice × (1 + upperThresholdPercent / 100)
 * lowerAlertPrice = referencePrice × (1 − lowerThresholdPercent / 100)
 * </pre>
 *
 * Both alert prices are {@code null} when {@code referencePrice} is {@code null}
 * (i.e., no market data was available when the threshold was saved).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdResponse {

    private Long   id;
    private String symbol;         // e.g. "RELIANCE.NS"
    private String displaySymbol;  // e.g. "RELIANCE"
    private String companyName;

    /** Upper threshold in percent (e.g. 5.00 = 5 %). */
    private BigDecimal upperThresholdPercent;

    /** Lower threshold in percent (e.g. 3.00 = 3 %). */
    private BigDecimal lowerThresholdPercent;

    /**
     * Market price at the time this threshold was last saved.
     * Alert prices are calculated relative to this value.
     * {@code null} if market data was unavailable at save time.
     */
    private BigDecimal referencePrice;

    /**
     * Pre-computed price level that triggers the upper alert.
     * Formula: {@code referencePrice × (1 + upperThresholdPercent / 100)}.
     * {@code null} if {@code referencePrice} is {@code null}.
     */
    private BigDecimal upperAlertPrice;

    /**
     * Pre-computed price level that triggers the lower alert.
     * Formula: {@code referencePrice × (1 − lowerThresholdPercent / 100)}.
     * {@code null} if {@code referencePrice} is {@code null}.
     */
    private BigDecimal lowerAlertPrice;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
