package com.practice.demo.service;

import com.practice.demo.constants.NseStocks;
import com.practice.demo.dto.StockQuote;
import com.practice.demo.dto.ThresholdRequest;
import com.practice.demo.dto.ThresholdResponse;
import com.practice.demo.entity.StockThreshold;
import com.practice.demo.entity.User;
import com.practice.demo.repository.StockThresholdRepository;
import com.practice.demo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Business logic for user-defined stock price thresholds.
 *
 * <p>Each threshold stores:
 * <ul>
 *   <li>{@code upperThresholdPercent} — positive %; alert when price rises ≥ this % above reference</li>
 *   <li>{@code lowerThresholdPercent} — positive %; alert when price falls ≥ this % below reference</li>
 *   <li>{@code referencePrice}        — market price captured at save time (may be null if cache empty)</li>
 * </ul>
 *
 * <p>The set operation is an <em>upsert</em>: first call creates the row; subsequent
 * calls for the same (user, symbol) pair update it in-place and refresh the reference price.
 */
@Service
public class ThresholdService {

    private static final Logger logger = LoggerFactory.getLogger(ThresholdService.class);

    private final StockThresholdRepository thresholdRepository;
    private final UserRepository           userRepository;
    private final StockService             stockService;

    public ThresholdService(StockThresholdRepository thresholdRepository,
                            UserRepository userRepository,
                            StockService stockService) {
        this.thresholdRepository = thresholdRepository;
        this.userRepository      = userRepository;
        this.stockService        = stockService;
    }

    // =========================================================================
    // Set / change threshold (upsert)
    // =========================================================================

    /**
     * Creates or updates the upper/lower price thresholds for {@code rawSymbol}.
     *
     * <ul>
     *   <li>First call for a symbol: inserts a new row.</li>
     *   <li>Subsequent calls: updates thresholds and refreshes the reference price
     *       from the current stock ticker cache.</li>
     * </ul>
     *
     * @param rawSymbol bare ticker, ".NS"-qualified, or company display name
     * @param request   upper and lower threshold percentages
     * @param username  the authenticated user
     * @return          the saved threshold including computed alert prices
     */
    @Transactional
    public ThresholdResponse setThreshold(String rawSymbol, ThresholdRequest request, String username) {
        String symbol = resolveAndValidateSymbol(rawSymbol);
        validatePercentages(request.getUpperThresholdPercent(),
                            request.getLowerThresholdPercent(), symbol);

        User       user   = fetchUser(username);
        BigDecimal refPx  = snapshotReferencePrice(symbol);

        Optional<StockThreshold> existing = thresholdRepository.findByUserAndSymbol(user, symbol);

        StockThreshold threshold;

        if (existing.isEmpty()) {
            // ── Create ──────────────────────────────────────────────────────
            threshold = StockThreshold.builder()
                    .user(user)
                    .symbol(symbol)
                    .companyName(NseStocks.DISPLAY_NAMES.getOrDefault(symbol, symbol.replace(".NS", "")))
                    .upperThresholdPercent(request.getUpperThresholdPercent().setScale(2, RoundingMode.HALF_UP))
                    .lowerThresholdPercent(request.getLowerThresholdPercent().setScale(2, RoundingMode.HALF_UP))
                    .referencePrice(refPx)
                    .build();

            logger.info("User '{}' — creating threshold for '{}': upper={}%, lower={}%, refPrice={}",
                    username, symbol,
                    request.getUpperThresholdPercent(), request.getLowerThresholdPercent(), refPx);

        } else {
            // ── Update ──────────────────────────────────────────────────────
            threshold = existing.get();

            logger.info("User '{}' — updating threshold for '{}': "
                            + "upper {}% → {}%, lower {}% → {}%, refPrice {} → {}",
                    username, symbol,
                    threshold.getUpperThresholdPercent(), request.getUpperThresholdPercent(),
                    threshold.getLowerThresholdPercent(), request.getLowerThresholdPercent(),
                    threshold.getReferencePrice(), refPx);

            threshold.setUpperThresholdPercent(request.getUpperThresholdPercent().setScale(2, RoundingMode.HALF_UP));
            threshold.setLowerThresholdPercent(request.getLowerThresholdPercent().setScale(2, RoundingMode.HALF_UP));
            threshold.setReferencePrice(refPx);
        }

        StockThreshold saved = thresholdRepository.save(threshold);
        logger.info("User '{}' — threshold {} for '{}' — upperAlert={}, lowerAlert={}",
                username, existing.isEmpty() ? "created" : "updated", symbol,
                computeUpperAlert(refPx, saved.getUpperThresholdPercent()),
                computeLowerAlert(refPx, saved.getLowerThresholdPercent()));

        return toResponse(saved);
    }

    // =========================================================================
    // Read
    // =========================================================================

    /**
     * Returns all threshold configurations for the user, sorted A→Z by symbol.
     */
    @Transactional(readOnly = true)
    public List<ThresholdResponse> getAllThresholds(String username) {
        User user = fetchUser(username);
        logger.info("User '{}' — fetching all thresholds", username);

        List<ThresholdResponse> list = thresholdRepository.findByUserOrderBySymbolAsc(user)
                .stream()
                .map(this::toResponse)
                .toList();

        logger.info("User '{}' — {} threshold(s) returned", username, list.size());
        return list;
    }

    /**
     * Returns the threshold for one specific stock.
     *
     * @throws IllegalArgumentException if no threshold has been set for that stock
     */
    @Transactional(readOnly = true)
    public ThresholdResponse getThreshold(String rawSymbol, String username) {
        String symbol = resolveAndValidateSymbol(rawSymbol);
        User   user   = fetchUser(username);

        logger.info("User '{}' — fetching threshold for '{}'", username, symbol);

        StockThreshold threshold = thresholdRepository.findByUserAndSymbol(user, symbol)
                .orElseThrow(() -> {
                    logger.warn("User '{}' — no threshold set for '{}'", username, symbol);
                    return new IllegalArgumentException(
                            "No threshold set for " + symbol.replace(".NS", "")
                            + ". Use PUT /api/thresholds/" + symbol.replace(".NS", "")
                            + " to create one.");
                });

        logger.info("User '{}' — threshold for '{}': upper={}%, lower={}%",
                username, symbol,
                threshold.getUpperThresholdPercent(), threshold.getLowerThresholdPercent());
        return toResponse(threshold);
    }

    // =========================================================================
    // Delete
    // =========================================================================

    /**
     * Removes the threshold for a stock.
     *
     * @throws IllegalArgumentException if no threshold exists for that stock
     */
    @Transactional
    public void deleteThreshold(String rawSymbol, String username) {
        String symbol = resolveAndValidateSymbol(rawSymbol);
        User   user   = fetchUser(username);

        logger.info("User '{}' — removing threshold for '{}'", username, symbol);

        StockThreshold threshold = thresholdRepository.findByUserAndSymbol(user, symbol)
                .orElseThrow(() -> {
                    logger.warn("User '{}' — delete failed: no threshold set for '{}'", username, symbol);
                    return new IllegalArgumentException(
                            "No threshold set for " + symbol.replace(".NS", "") + ".");
                });

        thresholdRepository.delete(threshold);
        logger.info("User '{}' — threshold for '{}' removed successfully", username, symbol);
    }

    // =========================================================================
    // Internals
    // =========================================================================

    /**
     * Reads the current market price for {@code symbol} from the StockService
     * in-memory cache and returns it as a 2-decimal BigDecimal.
     * Returns {@code null} if the cache is empty or the price is 0.
     */
    private BigDecimal snapshotReferencePrice(String symbol) {
        Map<String, StockQuote> quotes = stockService.getCurrentQuotesMap();
        StockQuote quote = quotes.get(symbol);
        if (quote == null || quote.getPrice() == 0.0) {
            logger.warn("No live market price available for '{}' — referencePrice stored as null", symbol);
            return null;
        }
        return BigDecimal.valueOf(quote.getPrice()).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Builds the API response, computing the absolute alert price levels on the fly.
     * Alert prices are {@code null} when {@code referencePrice} is {@code null}.
     */
    private ThresholdResponse toResponse(StockThreshold t) {
        BigDecimal ref   = t.getReferencePrice();
        BigDecimal upper = t.getUpperThresholdPercent();
        BigDecimal lower = t.getLowerThresholdPercent();

        return ThresholdResponse.builder()
                .id(t.getId())
                .symbol(t.getSymbol())
                .displaySymbol(t.getSymbol().replace(".NS", ""))
                .companyName(t.getCompanyName())
                .upperThresholdPercent(upper)
                .lowerThresholdPercent(lower)
                .referencePrice(ref)
                .upperAlertPrice(computeUpperAlert(ref, upper))
                .lowerAlertPrice(computeLowerAlert(ref, lower))
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    /**
     * {@code referencePrice × (1 + upperPct / 100)}, rounded to 2 decimals.
     * Returns {@code null} if {@code referencePrice} is {@code null}.
     */
    private BigDecimal computeUpperAlert(BigDecimal referencePrice, BigDecimal upperPct) {
        if (referencePrice == null) return null;
        // factor = 1 + upper / 100
        BigDecimal factor = BigDecimal.ONE.add(
                upperPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return referencePrice.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * {@code referencePrice × (1 − lowerPct / 100)}, rounded to 2 decimals.
     * Returns {@code null} if {@code referencePrice} is {@code null}.
     */
    private BigDecimal computeLowerAlert(BigDecimal referencePrice, BigDecimal lowerPct) {
        if (referencePrice == null) return null;
        // factor = 1 - lower / 100
        BigDecimal factor = BigDecimal.ONE.subtract(
                lowerPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return referencePrice.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    // ── Symbol normalization (mirrors PortfolioService) ─────────────────────

    /**
     * Normalises a user-supplied stock identifier to a canonical ".NS" symbol.
     * Accepts: bare ticker ("RELIANCE"), qualified ("RELIANCE.NS"), or company
     * display name ("Reliance Industries Limited"). Returns {@code null} if not
     * a recognised Nifty 50 stock.
     */
    private String normalizeSymbol(String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();
        String upper   = trimmed.toUpperCase();

        // Already qualified
        if (upper.endsWith(".NS")) {
            return NseStocks.SYMBOLS.contains(upper) ? upper : null;
        }

        // Bare ticker — try appending .NS
        String withNs = upper + ".NS";
        if (NseStocks.SYMBOLS.contains(withNs)) return withNs;

        // Display name lookup (case-insensitive)
        for (Map.Entry<String, String> entry : NseStocks.DISPLAY_NAMES.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(trimmed)) return entry.getKey();
        }
        return null;
    }

    private String resolveAndValidateSymbol(String input) {
        String symbol = normalizeSymbol(input);
        if (symbol == null) {
            throw new IllegalArgumentException(
                    "'" + input + "' is not a valid Nifty 50 stock. "
                    + "Use GET /api/portfolio/stocks to see the full list.");
        }
        return symbol;
    }

    private void validatePercentages(BigDecimal upper, BigDecimal lower, String symbol) {
        String display = symbol != null ? symbol.replace(".NS", "") : "";
        if (upper == null || upper.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "upperThresholdPercent must be a positive number for " + display
                    + " (e.g. 5.0 means 5 %).");
        }
        if (lower == null || lower.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "lowerThresholdPercent must be a positive number for " + display
                    + " (e.g. 3.0 means 3 %).");
        }
    }

    private User fetchUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logger.error("Authenticated user '{}' not found in DB", username);
                    return new IllegalStateException("Authenticated user not found: " + username);
                });
    }
}
