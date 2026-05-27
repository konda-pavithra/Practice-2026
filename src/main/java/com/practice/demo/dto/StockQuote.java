package com.practice.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockQuote {

    /** Full Yahoo Finance symbol, e.g. "RELIANCE.NS" */
    private String symbol;

    /** Exchange-only ticker, e.g. "RELIANCE" */
    private String displaySymbol;

    private String companyName;
    private double price;
    private double change;
    private double changePercent;

    private double open;
    private double high;
    private double low;
    private double previousClose;
    private long   volume;

    private String currency;

    /**
     * Market state reported by Yahoo Finance.
     * Typical values: "REGULAR", "PRE", "POST", "CLOSED"
     */
    private String marketState;

    /** true when the day's change is positive (green ticker). */
    private boolean gainDay;

    private LocalDateTime lastUpdated;
}
