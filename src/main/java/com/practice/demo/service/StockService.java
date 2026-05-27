package com.practice.demo.service;

import com.practice.demo.client.YahooFinanceClient;
import com.practice.demo.client.dto.YahooFinanceQuoteResponse.YahooQuote;
import com.practice.demo.constants.NseStocks;
import com.practice.demo.dto.StockQuote;
import com.practice.demo.dto.StockTickerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Core service for the Nifty 50 stock ticker.
 *
 * Responsibilities:
 *  1. Fetch fresh quotes from Yahoo Finance via {@link YahooFinanceClient}.
 *  2. Hold the latest quotes in a thread-safe in-memory cache.
 *
 * The scheduler calls {@link #refreshQuotes()} every 30 seconds.
 * The controller reads {@link #getCurrentTickerResponse()} on each poll request.
 *
 * Thread-safety note:
 *  {@code cachedQuotes}, {@code lastFetchedAt}, and {@code dataStatus} are
 *  declared {@code volatile} and replaced atomically (never mutated in-place),
 *  so HTTP handler threads always see a consistent snapshot without locking.
 */
@Service
public class StockService {

    private static final Logger logger = LoggerFactory.getLogger(StockService.class);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final YahooFinanceClient yahooFinanceClient;

    // ── In-memory cache ──────────────────────────────────────────────────────
    private volatile List<StockQuote> cachedQuotes  = Collections.emptyList();
    private volatile LocalDateTime    lastFetchedAt = null;
    private volatile String           dataStatus    = "UNAVAILABLE";

    public StockService(YahooFinanceClient yahooFinanceClient) {
        this.yahooFinanceClient = yahooFinanceClient;
    }

    // -----------------------------------------------------------------------
    // Scheduled entry-point (called by StockPriceScheduler)
    // -----------------------------------------------------------------------

    /**
     * Fetches the latest Nifty 50 quotes from Yahoo Finance and updates
     * the in-memory cache. On failure, the previous cache is preserved
     * so polling clients continue to receive the last known prices.
     */
    public void refreshQuotes() {
        logger.info("Refreshing Nifty 50 quotes — fetching {} symbols from Yahoo Finance",
                NseStocks.SYMBOLS.size());

        try {
            List<YahooQuote> rawQuotes = yahooFinanceClient.fetchQuotes(NseStocks.SYMBOLS);

            if (rawQuotes.isEmpty()) {
                logger.warn("Yahoo Finance returned 0 quotes — keeping previous cache");
                return;
            }

            List<StockQuote> freshQuotes = rawQuotes.stream()
                    .map(this::mapToStockQuote)
                    .toList();

            // Atomic cache replacement
            cachedQuotes  = freshQuotes;
            lastFetchedAt = LocalDateTime.now();
            dataStatus    = "LIVE";

            logger.info("Cache updated: {} quotes, market open={}", freshQuotes.size(), isMarketOpen());

        } catch (Exception ex) {
            logger.error("Unexpected error during quote refresh: {}", ex.getMessage(), ex);
            // Cache remains unchanged — polling clients continue to see last good data
        }
    }

    // -----------------------------------------------------------------------
    // REST query (called by StockController on each poll)
    // -----------------------------------------------------------------------

    public StockTickerResponse getCurrentTickerResponse() {
        if (cachedQuotes.isEmpty()) {
            return StockTickerResponse.builder()
                    .stocks(Collections.emptyList())
                    .count(0)
                    .marketOpen(isMarketOpen())
                    .fetchedAt(LocalDateTime.now())
                    .dataStatus("UNAVAILABLE")
                    .message("Stock data is being loaded. Please retry in a few seconds.")
                    .build();
        }
        return buildResponse(dataStatus);
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private StockTickerResponse buildResponse(String status) {
        return StockTickerResponse.builder()
                .stocks(cachedQuotes)
                .count(cachedQuotes.size())
                .marketOpen(isMarketOpen())
                .fetchedAt(lastFetchedAt != null ? lastFetchedAt : LocalDateTime.now())
                .dataStatus(status)
                .message(isMarketOpen()
                        ? "Market is open — live prices"
                        : "Market is closed — last traded prices")
                .build();
    }

    // ── Mapping: YahooQuote → StockQuote ────────────────────────────────────

    private StockQuote mapToStockQuote(YahooQuote yq) {
        String symbol        = yq.getSymbol() != null ? yq.getSymbol() : "UNKNOWN";
        String displaySymbol = symbol.replace(".NS", "");
        double change        = safeDouble(yq.getRegularMarketChange());

        return StockQuote.builder()
                .symbol(symbol)
                .displaySymbol(displaySymbol)
                .companyName(resolveCompanyName(symbol, yq.getLongName(), yq.getShortName()))
                .price(safeDouble(yq.getRegularMarketPrice()))
                .change(change)
                .changePercent(safeDouble(yq.getRegularMarketChangePercent()))
                .open(safeDouble(yq.getRegularMarketOpen()))
                .high(safeDouble(yq.getRegularMarketDayHigh()))
                .low(safeDouble(yq.getRegularMarketDayLow()))
                .previousClose(safeDouble(yq.getRegularMarketPreviousClose()))
                .volume(safeLong(yq.getRegularMarketVolume()))
                .currency(yq.getCurrency() != null ? yq.getCurrency() : "INR")
                .marketState(yq.getMarketState() != null ? yq.getMarketState() : "UNKNOWN")
                .gainDay(change >= 0)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    private String resolveCompanyName(String symbol, String longName, String shortName) {
        String curated = NseStocks.DISPLAY_NAMES.get(symbol);
        if (curated  != null) return curated;
        if (longName  != null && !longName.isBlank())  return longName;
        if (shortName != null && !shortName.isBlank()) return shortName;
        return symbol.replace(".NS", "");
    }

    // ── Market-hours check (IST) ─────────────────────────────────────────────

    /**
     * NSE trades Monday–Friday, 09:15–15:30 IST.
     * Public holidays are not accounted for here.
     */
    private boolean isMarketOpen() {
        ZonedDateTime now  = ZonedDateTime.now(IST);
        DayOfWeek     day  = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return false;
        LocalTime time      = now.toLocalTime();
        LocalTime openTime  = LocalTime.of(9, 15);
        LocalTime closeTime = LocalTime.of(15, 30);
        return !time.isBefore(openTime) && !time.isAfter(closeTime);
    }

    // ── Null-safe helpers ────────────────────────────────────────────────────

    private static double safeDouble(Double value) { return value != null ? value : 0.0; }
    private static long   safeLong  (Long   value) { return value != null ? value : 0L;  }
}
