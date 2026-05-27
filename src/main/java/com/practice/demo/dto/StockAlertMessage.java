package com.practice.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The JSON payload published to {@code stock.alerts.queue} by the alert generator
 * and consumed by {@link com.practice.demo.consumer.StockAlertConsumer}.
 *
 * <p>Contains everything the email service needs to compose a complete alert email —
 * user contact info, stock identity, threshold details, and portfolio P&amp;L — so the
 * consumer requires no further database lookups.
 *
 * <p>Jackson serializes this to JSON via {@code Jackson2JsonMessageConverter}.
 * A {@code __TypeId__} AMQP header is added automatically for deserialization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAlertMessage implements Serializable {

    // ── Recipient ─────────────────────────────────────────────────────────────

    private String username;
    private String userEmail;

    // ── Stock identity ────────────────────────────────────────────────────────

    private String symbol;        // e.g. "RELIANCE.NS"
    private String displaySymbol; // e.g. "RELIANCE"
    private String companyName;

    // ── Threshold details ─────────────────────────────────────────────────────

    /**
     * Which threshold was breached: {@code "UPPER"} or {@code "LOWER"}.
     */
    private String alertType;

    /** The configured threshold percentage (e.g. 5.00 = 5 %). */
    private BigDecimal thresholdPercent;

    /** Market price captured when the threshold was last saved. */
    private BigDecimal referencePrice;

    /** Absolute price level that triggered the alert (upper or lower). */
    private BigDecimal alertPrice;

    /** Current live market price at alert-generation time. */
    private BigDecimal currentPrice;

    // ── Portfolio context ─────────────────────────────────────────────────────

    private Integer    quantity;          // shares held
    private BigDecimal buyingPrice;       // average buy price per share
    private BigDecimal investmentValue;   // quantity × buyingPrice
    private BigDecimal currentValue;      // quantity × currentPrice
    private BigDecimal profitLoss;        // currentValue − investmentValue
    private double     plPercent;         // (profitLoss / investmentValue) × 100
    private boolean    gain;              // true when profitLoss ≥ 0

    // ── Metadata ──────────────────────────────────────────────────────────────

    private LocalDateTime alertGeneratedAt;
}
