package com.practice.demo.dto;

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
@Data
public class AddStockRequest {

    private String     symbol;
    private Integer    quantity;
    private BigDecimal buyingPrice;
}
