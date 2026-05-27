package com.practice.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a single stock holding inside a user's portfolio.
 * The combination of (user, symbol) is unique — one row per stock per user.
 */
@Entity
@Table(
    name = "portfolio",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_portfolio_user_symbol",
        columnNames = {"user_id", "symbol"}
    )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Yahoo Finance symbol, e.g. "RELIANCE.NS" */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** Human-readable company name, e.g. "Reliance Industries" */
    @Column(name = "company_name", nullable = false)
    private String companyName;

    /** Number of shares held. */
    @Column(nullable = false)
    private Integer quantity;

    /** Average buying price per share in INR. */
    @Column(name = "buying_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal buyingPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
