package com.practice.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTickerResponse {

    private List<StockQuote> stocks;
    private int              count;
    private boolean          marketOpen;
    private LocalDateTime    fetchedAt;

    /**
     * "LIVE"        — freshly fetched from Yahoo Finance
     * "CACHED"      — previous successful fetch, reused after API error
     * "UNAVAILABLE" — no data at all yet
     */
    private String dataStatus;

    private String message;
}
