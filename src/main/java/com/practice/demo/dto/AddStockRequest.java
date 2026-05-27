package com.practice.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for manually adding or updating a single stock holding.
 *
 * The {@code symbol} field is flexible — the service accepts any of:
 * <ul>
 *   <li>"RELIANCE"           (bare ticker)</li>
 *   <li>"RELIANCE.NS"        (qualified Yahoo Finance symbol)</li>
 *   <li>"Reliance Industries"(display name, case-insensitive)</li>
 * </ul>
 */
@Schema(description = "Payload for adding or updating a single stock holding in the portfolio")
@Data
public class AddStockRequest {

    @Schema(
        description = "Stock identifier — accepts bare ticker (RELIANCE), "
                    + "qualified form (RELIANCE.NS), or company display name (case-insensitive)",
        example = "RELIANCE"
    )
    private String symbol;

    @Schema(description = "Number of shares held (must be ≥ 1)", example = "10")
    private Integer quantity;

    @Schema(description = "Average buying price per share in ₹ (must be > 0)", example = "2450.50")
    private BigDecimal buyingPrice;
}
