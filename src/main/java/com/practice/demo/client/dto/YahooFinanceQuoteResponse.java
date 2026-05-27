package com.practice.demo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Mirrors the JSON structure returned by the Yahoo Finance v7 quote endpoint:
 * https://query2.finance.yahoo.com/v7/finance/quote?symbols=...
 *
 * All nested classes carry @JsonIgnoreProperties(ignoreUnknown = true) because
 * Yahoo Finance's response contains many fields we don't need.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class YahooFinanceQuoteResponse {

    private QuoteResponse quoteResponse;

    // -----------------------------------------------------------------------

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuoteResponse {
        private List<YahooQuote> result;
        private Object           error;
    }

    // -----------------------------------------------------------------------

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class YahooQuote {

        private String symbol;
        private String shortName;
        private String longName;

        // Price data
        private Double regularMarketPrice;
        private Double regularMarketChange;
        private Double regularMarketChangePercent; // e.g. -0.48 means -0.48 %
        private Double regularMarketOpen;
        private Double regularMarketDayHigh;
        private Double regularMarketDayLow;
        private Double regularMarketPreviousClose;
        private Long   regularMarketVolume;

        private String currency;

        /**
         * Possible values from Yahoo Finance:
         *   "REGULAR"  — market is open, live prices
         *   "PRE"      — pre-market session
         *   "POST"     — after-hours session
         *   "CLOSED"   — market is closed, showing last-trade price
         */
        private String marketState;
    }
}
