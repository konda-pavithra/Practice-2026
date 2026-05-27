package com.practice.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request body for {@code PUT /api/thresholds/{symbol}}.
 *
 * Both percentages must be positive (> 0).
 * The stock symbol is supplied as a path variable, not in the body.
 *
 * <p>Example:
 * <pre>
 * {
 *   "upperThresholdPercent": 5.0,
 *   "lowerThresholdPercent": 3.0
 * }
 * </pre>
 * Interpretation: alert when the stock price rises ≥ 5 % above or
 * falls ≥ 3 % below the market price recorded at the time of saving.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdRequest {

    /**
     * Upper alert threshold as a positive percentage.
     * Example: 5.0 means 5 % above the reference price.
     */
    private BigDecimal upperThresholdPercent;

    /**
     * Lower alert threshold as a positive percentage.
     * Example: 3.0 means 3 % below the reference price.
     */
    private BigDecimal lowerThresholdPercent;
}
