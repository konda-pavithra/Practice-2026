package com.practice.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Upper and lower price-alert thresholds expressed as positive percentages "
                     + "relative to the market price at save time")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdRequest {

    /**
     * Upper alert threshold as a positive percentage.
     * Example: 5.0 means 5 % above the reference price.
     */
    @Schema(
        description = "Upper alert threshold — positive percentage above the reference price. "
                    + "Alert fires when currentPrice ≥ referencePrice × (1 + upper / 100).",
        example = "5.0"
    )
    private BigDecimal upperThresholdPercent;

    /**
     * Lower alert threshold as a positive percentage.
     * Example: 3.0 means 3 % below the reference price.
     */
    @Schema(
        description = "Lower alert threshold — positive percentage below the reference price. "
                    + "Alert fires when currentPrice ≤ referencePrice × (1 − lower / 100).",
        example = "3.0"
    )
    private BigDecimal lowerThresholdPercent;
}
