package com.practice.demo.dto;

/**
 * Describes the relationship between a stock's current market price and the
 * user-defined upper/lower threshold levels for that stock.
 *
 * <p>Computed per holding inside {@link com.practice.demo.service.PortfolioRealtimeService}
 * using the formula:
 * <pre>
 *   upperAlertPrice = referencePrice × (1 + upperThresholdPercent / 100)
 *   lowerAlertPrice = referencePrice × (1 − lowerThresholdPercent / 100)
 *
 *   ABOVE_UPPER   →  currentPrice ≥ upperAlertPrice
 *   BELOW_LOWER   →  currentPrice ≤ lowerAlertPrice
 *   WITHIN_BOUNDS →  lowerAlertPrice < currentPrice < upperAlertPrice
 *   NO_THRESHOLD  →  user has not configured any threshold for this stock
 * </pre>
 *
 * <p>The UI uses this to colour-code each holding row in the portfolio table.
 */
public enum ThresholdStatus {

    /** Current price has risen to or above the upper alert level. */
    ABOVE_UPPER,

    /** Current price has fallen to or below the lower alert level. */
    BELOW_LOWER,

    /** Current price is between the lower and upper alert levels — within normal range. */
    WITHIN_BOUNDS,

    /** No threshold has been configured for this stock by the user. */
    NO_THRESHOLD
}
