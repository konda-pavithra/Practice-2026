package com.practice.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Read-model for a single portfolio holding returned by {@code GET /api/portfolio}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioResponse {

    private Long          id;
    private String        symbol;
    private String        displaySymbol;
    private String        companyName;
    private Integer       quantity;
    private BigDecimal    buyingPrice;
    private BigDecimal    totalInvestment;  // quantity × buyingPrice
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
