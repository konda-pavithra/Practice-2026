package com.practice.demo.service;

import com.practice.demo.dto.*;
import com.practice.demo.entity.Portfolio;
import com.practice.demo.entity.StockThreshold;
import com.practice.demo.entity.User;
import com.practice.demo.event.StockPricesUpdatedEvent;
import com.practice.demo.repository.PortfolioRepository;
import com.practice.demo.repository.StockThresholdRepository;
import com.practice.demo.repository.UserRepository;
import com.practice.demo.store.LivePriceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Real-time portfolio valuation service.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li><b>SSE registration</b> — maintains a {@code username → SseEmitter} map
 *       for all currently connected clients.</li>
 *   <li><b>Price-update listener</b> — reacts to every {@link StockPricesUpdatedEvent}
 *       fired by the Kafka consumer and pushes fresh valuations to all clients.</li>
 *   <li><b>Valuation computation</b> — uses the <b>Java Streams API</b> to transform
 *       each portfolio holding into a {@link HoldingRealtimeValuation} and aggregate
 *       portfolio-level totals.</li>
 *   <li><b>Heartbeat</b> — sends an SSE comment every 30 s to keep idle connections
 *       alive through proxies and firewalls.</li>
 * </ol>
 *
 * <h3>Data sources</h3>
 * <pre>
 *   LivePriceStore  ← Kafka consumer  (primary — real-time)
 *   StockService    ← volatile cache  (fallback — if Kafka store is empty on startup)
 * </pre>
 *
 * <h3>Streams API usage</h3>
 * <ul>
 *   <li>{@code stream().map()} — per-holding valuation and threshold evaluation</li>
 *   <li>{@code stream().sorted()} — holdings sorted alphabetically by company name</li>
 *   <li>{@code stream().map().reduce()} — portfolio total investment / current value</li>
 *   <li>{@code stream().filter().count()} — threshold breach counts by status</li>
 *   <li>{@code stream().collect(Collectors.groupingBy())} — thresholds indexed by symbol</li>
 * </ul>
 */
@Service
public class PortfolioRealtimeService {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioRealtimeService.class);

    private final LivePriceStore           livePriceStore;
    private final StockService             stockService;
    private final PortfolioRepository      portfolioRepository;
    private final StockThresholdRepository thresholdRepository;
    private final UserRepository           userRepository;

    /** username → active SSE emitter; one connection per user enforced. */
    private final ConcurrentHashMap<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    public PortfolioRealtimeService(LivePriceStore livePriceStore,
                                    StockService stockService,
                                    PortfolioRepository portfolioRepository,
                                    StockThresholdRepository thresholdRepository,
                                    UserRepository userRepository) {
        this.livePriceStore      = livePriceStore;
        this.stockService        = stockService;
        this.portfolioRepository = portfolioRepository;
        this.thresholdRepository = thresholdRepository;
        this.userRepository      = userRepository;
    }

    // =========================================================================
    // SSE emitter lifecycle
    // =========================================================================

    /**
     * Creates and registers a new SSE emitter for {@code username}.
     *
     * <p>If the user already has an open connection it is completed (closed) before
     * the new emitter is registered, ensuring at most one connection per user.
     *
     * <p>The initial portfolio valuation is pushed immediately so the client
     * does not wait up to 30 s for the first data.
     *
     * @param username     authenticated user
     * @param timeoutMs    emitter timeout in milliseconds (from config)
     * @return             the new emitter to be returned from the controller
     */
    public SseEmitter register(String username, long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);

        // Clean up on close / error / timeout
        emitter.onCompletion(() -> {
            activeEmitters.remove(username);
            logger.info("SSE — connection closed for user '{}'", username);
        });
        emitter.onTimeout(() -> {
            activeEmitters.remove(username);
            logger.info("SSE — connection timed out for user '{}'", username);
        });
        emitter.onError(ex -> {
            activeEmitters.remove(username);
            logger.warn("SSE — connection error for user '{}': {}", username, ex.getMessage());
        });

        // Replace any existing connection for this user
        SseEmitter previous = activeEmitters.put(username, emitter);
        if (previous != null) {
            logger.debug("SSE — replacing existing connection for user '{}'", username);
            previous.complete();
        }

        logger.info("SSE — registered new connection for user '{}' (active={})",
                username, activeEmitters.size());

        // Push initial snapshot immediately — user sees data right away
        pushToEmitter(username, emitter);

        return emitter;
    }

    /** Number of currently connected SSE clients. */
    public int activeConnectionCount() {
        return activeEmitters.size();
    }

    // =========================================================================
    // Kafka event → SSE push
    // =========================================================================

    /**
     * Fires on every {@link StockPricesUpdatedEvent} published by
     * {@link com.practice.demo.consumer.StockPriceKafkaConsumer}.
     *
     * <p>One event per 30-second Kafka batch → one SSE push per connected client.
     */
    @EventListener
    public void onStockPricesUpdated(StockPricesUpdatedEvent event) {
        if (activeEmitters.isEmpty()) {
            logger.debug("SSE — no active connections, skipping push");
            return;
        }

        logger.debug("SSE — pushing portfolio updates to {} client(s) after Kafka batch ({} quotes)",
                activeEmitters.size(), event.getUpdatedQuotes().size());

        activeEmitters.forEach(this::pushToEmitter);
    }

    // =========================================================================
    // Heartbeat — keeps long-lived SSE connections alive
    // =========================================================================

    /**
     * Sends an SSE comment (heartbeat) to every connected client every 30 s.
     * Comments are invisible to the {@code EventSource.onmessage} handler but
     * prevent proxies and load balancers from closing idle connections.
     */
    @Scheduled(fixedDelayString = "${stock.refresh.interval-ms:30000}",
               initialDelayString = "${stock.refresh.interval-ms:30000}")
    public void sendHeartbeats() {
        if (activeEmitters.isEmpty()) return;

        logger.debug("SSE — sending heartbeat to {} client(s)", activeEmitters.size());

        activeEmitters.forEach((username, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException ex) {
                logger.debug("SSE — heartbeat failed for '{}', removing emitter", username);
                emitter.complete();
                activeEmitters.remove(username);
            }
        });
    }

    // =========================================================================
    // Core: push a portfolio valuation to one client
    // =========================================================================

    private void pushToEmitter(String username, SseEmitter emitter) {
        try {
            PortfolioRealtimeResponse payload = computeValuation(username);
            emitter.send(SseEmitter.event()
                    .name("portfolio-update")
                    .data(payload, MediaType.APPLICATION_JSON));

            logger.debug("SSE — pushed to '{}': {} holdings, P&L=₹{}, dataStatus={}",
                    username,
                    payload.getTotalHoldings(),
                    payload.getTotalProfitLoss(),
                    payload.getDataStatus());

        } catch (IOException ex) {
            logger.warn("SSE — client '{}' disconnected mid-push, removing emitter", username);
            emitter.complete();
            activeEmitters.remove(username);
        } catch (Exception ex) {
            logger.error("SSE — unexpected error computing valuation for '{}': {}",
                    username, ex.getMessage(), ex);
        }
    }

    // =========================================================================
    // Valuation computation (Java Streams API)
    // =========================================================================

    /**
     * Computes a complete portfolio valuation for {@code username} by joining:
     * <ul>
     *   <li>Portfolio holdings (from DB)</li>
     *   <li>Live prices (from {@link LivePriceStore} / Kafka pipeline)</li>
     *   <li>User-defined thresholds (from DB)</li>
     * </ul>
     *
     * <p><b>Java Streams API is used throughout</b> — see the method body for
     * {@code map}, {@code sorted}, {@code reduce}, {@code filter}, {@code collect},
     * and {@code groupingBy} operations.
     */
    @Transactional(readOnly = true)
    public PortfolioRealtimeResponse computeValuation(String username) {

        // ── 1. Load user ─────────────────────────────────────────────────────
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            logger.error("computeValuation — user '{}' not found", username);
            return emptyResponse();
        }
        User user = userOpt.get();

        // ── 2. Load portfolio holdings and thresholds ────────────────────────
        List<Portfolio>       holdings   = portfolioRepository.findByUserOrderBySymbolAsc(user);
        List<StockThreshold>  thresholds = thresholdRepository.findByUserOrderBySymbolAsc(user);

        if (holdings.isEmpty()) {
            logger.debug("computeValuation — '{}' has no holdings", username);
            return emptyResponse();
        }

        // ── 3. Build price map and threshold map using Streams ───────────────
        // Price map: choose LivePriceStore (Kafka) as primary, fall back to StockService cache
        Map<String, StockPriceMessage> liveMap   = livePriceStore.getAll();
        Map<String, StockQuote>        cachedMap  = stockService.getCurrentQuotesMap();
        boolean                         usingLive = !liveMap.isEmpty();
        String                          dataStatus = usingLive  ? "LIVE"
                                                   : !cachedMap.isEmpty() ? "CACHED"
                                                   : "UNAVAILABLE";

        // Streams: index thresholds by symbol for O(1) lookup per holding
        Map<String, StockThreshold> thresholdBySymbol = thresholds.stream()
                .collect(Collectors.toMap(StockThreshold::getSymbol, t -> t));

        // ── 4. Per-holding valuation — Streams .map() + .sorted() ────────────
        List<HoldingRealtimeValuation> holdingValuations = holdings.stream()
                .map(holding -> {
                    BigDecimal currentPrice = resolveCurrentPrice(
                            holding.getSymbol(), liveMap, cachedMap, usingLive);
                    StockThreshold threshold = thresholdBySymbol.get(holding.getSymbol());
                    return buildHoldingValuation(holding, currentPrice, threshold, liveMap, cachedMap, usingLive);
                })
                // Sort alphabetically by company name for a consistent UI display
                .sorted(Comparator.comparing(HoldingRealtimeValuation::getCompanyName,
                        String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        // ── 5. Portfolio-level aggregates — Streams .reduce() ────────────────
        BigDecimal totalInvestment = holdingValuations.stream()
                .map(HoldingRealtimeValuation::getInvestmentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCurrentValue = holdingValuations.stream()
                .map(HoldingRealtimeValuation::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalProfitLoss = totalCurrentValue.subtract(totalInvestment)
                .setScale(2, RoundingMode.HALF_UP);

        double totalPLPercent = totalInvestment.compareTo(BigDecimal.ZERO) > 0
                ? totalProfitLoss
                    .divide(totalInvestment, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue()
                : 0.0;

        // ── 6. Threshold breach summary — Streams .filter().count() ──────────
        long aboveUpper   = holdingValuations.stream()
                .filter(h -> h.getThresholdStatus() == ThresholdStatus.ABOVE_UPPER)
                .count();

        long belowLower   = holdingValuations.stream()
                .filter(h -> h.getThresholdStatus() == ThresholdStatus.BELOW_LOWER)
                .count();

        long withinBounds = holdingValuations.stream()
                .filter(h -> h.getThresholdStatus() == ThresholdStatus.WITHIN_BOUNDS)
                .count();

        long noThreshold  = holdingValuations.stream()
                .filter(h -> h.getThresholdStatus() == ThresholdStatus.NO_THRESHOLD)
                .count();

        logger.debug("computeValuation — '{}': {} holdings, invested=₹{}, current=₹{}, " +
                        "P&L=₹{} ({}%), ↑{}  ↓{}  ⚖{}  ∅{}, dataStatus={}",
                username, holdingValuations.size(),
                totalInvestment, totalCurrentValue,
                totalProfitLoss, totalPLPercent,
                aboveUpper, belowLower, withinBounds, noThreshold, dataStatus);

        return PortfolioRealtimeResponse.builder()
                .holdings(holdingValuations)
                .totalHoldings(holdingValuations.size())
                .totalInvestment(totalInvestment.setScale(2, RoundingMode.HALF_UP))
                .totalCurrentValue(totalCurrentValue.setScale(2, RoundingMode.HALF_UP))
                .totalProfitLoss(totalProfitLoss)
                .totalPLPercent(totalPLPercent)
                .holdingsAboveUpperThreshold((int) aboveUpper)
                .holdingsBelowLowerThreshold((int) belowLower)
                .holdingsWithinBounds((int) withinBounds)
                .holdingsWithoutThreshold((int) noThreshold)
                .dataStatus(dataStatus)
                .valuedAt(LocalDateTime.now())
                .build();
    }

    // =========================================================================
    // Per-holding helper
    // =========================================================================

    private HoldingRealtimeValuation buildHoldingValuation(
            Portfolio holding,
            BigDecimal currentPrice,
            StockThreshold threshold,
            Map<String, StockPriceMessage> liveMap,
            Map<String, StockQuote> cachedMap,
            boolean usingLive) {

        String symbol = holding.getSymbol();

        BigDecimal investmentValue = holding.getBuyingPrice()
                .multiply(BigDecimal.valueOf(holding.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal currentValue = currentPrice
                .multiply(BigDecimal.valueOf(holding.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal profitLoss = currentValue.subtract(investmentValue)
                .setScale(2, RoundingMode.HALF_UP);

        double plPercent = investmentValue.compareTo(BigDecimal.ZERO) > 0
                ? profitLoss
                    .divide(investmentValue, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue()
                : 0.0;

        // Day-change percent and market state from price source
        double dayChangePct = 0.0;
        String marketState  = "UNKNOWN";
        if (usingLive && liveMap.containsKey(symbol)) {
            StockPriceMessage msg = liveMap.get(symbol);
            dayChangePct = msg.getChangePercent();
            marketState  = msg.getMarketState() != null ? msg.getMarketState() : "UNKNOWN";
        } else if (cachedMap.containsKey(symbol)) {
            StockQuote q = cachedMap.get(symbol);
            dayChangePct = q.getChangePercent();
            marketState  = q.getMarketState() != null ? q.getMarketState() : "UNKNOWN";
        }

        // Threshold status
        ThresholdStatus  status           = ThresholdStatus.NO_THRESHOLD;
        BigDecimal       upperThresholdPct = null;
        BigDecimal       lowerThresholdPct = null;
        BigDecimal       upperAlertPrice   = null;
        BigDecimal       lowerAlertPrice   = null;

        if (threshold != null && threshold.getReferencePrice() != null
                && threshold.getReferencePrice().compareTo(BigDecimal.ZERO) > 0) {

            upperThresholdPct = threshold.getUpperThresholdPercent();
            lowerThresholdPct = threshold.getLowerThresholdPercent();

            BigDecimal ref = threshold.getReferencePrice();

            upperAlertPrice = ref.multiply(
                    BigDecimal.ONE.add(upperThresholdPct.divide(
                            BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP);

            lowerAlertPrice = ref.multiply(
                    BigDecimal.ONE.subtract(lowerThresholdPct.divide(
                            BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP);

            if (currentPrice.compareTo(upperAlertPrice) >= 0) {
                status = ThresholdStatus.ABOVE_UPPER;
            } else if (currentPrice.compareTo(lowerAlertPrice) <= 0) {
                status = ThresholdStatus.BELOW_LOWER;
            } else {
                status = ThresholdStatus.WITHIN_BOUNDS;
            }
        }

        return HoldingRealtimeValuation.builder()
                .symbol(symbol)
                .displaySymbol(symbol.replace(".NS", ""))
                .companyName(holding.getCompanyName())
                .quantity(holding.getQuantity())
                .buyingPrice(holding.getBuyingPrice())
                .investmentValue(investmentValue)
                .currentPrice(currentPrice)
                .currentValue(currentValue)
                .dayChangePercent(dayChangePct)
                .marketState(marketState)
                .profitLoss(profitLoss)
                .plPercent(plPercent)
                .gain(profitLoss.compareTo(BigDecimal.ZERO) >= 0)
                .thresholdStatus(status)
                .upperThresholdPercent(upperThresholdPct)
                .lowerThresholdPercent(lowerThresholdPct)
                .upperAlertPrice(upperAlertPrice)
                .lowerAlertPrice(lowerAlertPrice)
                .build();
    }

    // =========================================================================
    // Price resolution (Kafka store → StockService fallback)
    // =========================================================================

    private BigDecimal resolveCurrentPrice(String symbol,
                                           Map<String, StockPriceMessage> liveMap,
                                           Map<String, StockQuote> cachedMap,
                                           boolean preferLive) {
        if (preferLive) {
            StockPriceMessage msg = liveMap.get(symbol);
            if (msg != null && msg.getPrice() > 0) {
                return BigDecimal.valueOf(msg.getPrice()).setScale(2, RoundingMode.HALF_UP);
            }
        }
        StockQuote cached = cachedMap.get(symbol);
        if (cached != null && cached.getPrice() > 0) {
            return BigDecimal.valueOf(cached.getPrice()).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private PortfolioRealtimeResponse emptyResponse() {
        return PortfolioRealtimeResponse.builder()
                .holdings(Collections.emptyList())
                .totalHoldings(0)
                .totalInvestment(BigDecimal.ZERO)
                .totalCurrentValue(BigDecimal.ZERO)
                .totalProfitLoss(BigDecimal.ZERO)
                .totalPLPercent(0.0)
                .holdingsAboveUpperThreshold(0)
                .holdingsBelowLowerThreshold(0)
                .holdingsWithinBounds(0)
                .holdingsWithoutThreshold(0)
                .dataStatus("UNAVAILABLE")
                .valuedAt(LocalDateTime.now())
                .build();
    }
}
