package com.practice.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stores a user's upper and lower price-alert thresholds for a single Nifty 50 stock.
 *
 * <p>Both thresholds are expressed as positive percentages relative to the
 * {@code referencePrice} — the market price captured at the moment the threshold
 * was last saved.
 *
 * <ul>
 *   <li>Upper alert triggers when  currentPrice ≥ referencePrice × (1 + upper / 100)</li>
 *   <li>Lower alert triggers when  currentPrice ≤ referencePrice × (1 − lower / 100)</li>
 * </ul>
 *
 * A user can hold at most one threshold row per stock (unique constraint on user_id + symbol).
 * Saving a new threshold for an existing symbol is an in-place update (upsert).
 */
@Entity
@Table(
    name = "stock_thresholds",
    uniqueConstraints = @UniqueConstraint(
        name  = "uq_threshold_user_symbol",
        columnNames = {"user_id", "symbol"}
    )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Canonical Yahoo Finance symbol, e.g. "RELIANCE.NS". */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** Human-readable company name, e.g. "Reliance Industries Limited". */
    @Column(nullable = false, name = "company_name")
    private String companyName;

    /**
     * Upper price threshold as a positive percentage.
     * E.g. 5.00 means "alert when price is 5 % above the reference price".
     */
    @Column(nullable = false, precision = 8, scale = 2, name = "upper_threshold_percent")
    private BigDecimal upperThresholdPercent;

    /**
     * Lower price threshold as a positive percentage.
     * E.g. 3.00 means "alert when price is 3 % below the reference price".
     */
    @Column(nullable = false, precision = 8, scale = 2, name = "lower_threshold_percent")
    private BigDecimal lowerThresholdPercent;

    /**
     * Market price at the moment this threshold was last saved.
     * Alert prices are computed relative to this value.
     * {@code null} when the stock ticker cache had no data at save time.
     */
    @Column(precision = 12, scale = 2, name = "reference_price")
    private BigDecimal referencePrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
