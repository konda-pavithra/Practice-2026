package com.practice.demo.service;

import com.practice.demo.constants.NseStocks;
import com.practice.demo.dto.*;
import com.practice.demo.entity.Portfolio;
import com.practice.demo.entity.User;
import com.practice.demo.exception.StockAlreadyInPortfolioException;
import com.practice.demo.repository.PortfolioRepository;
import com.practice.demo.repository.UserRepository;
import com.practice.demo.util.ExcelParser;
import com.practice.demo.util.ExcelParser.ParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PortfolioService {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioService.class);

    private final PortfolioRepository portfolioRepository;
    private final UserRepository      userRepository;
    private final ExcelParser         excelParser;
    private final StockService        stockService;

    public PortfolioService(PortfolioRepository portfolioRepository,
                            UserRepository userRepository,
                            ExcelParser excelParser,
                            StockService stockService) {
        this.portfolioRepository = portfolioRepository;
        this.userRepository      = userRepository;
        this.excelParser         = excelParser;
        this.stockService        = stockService;
    }

    // =======================================================================
    // A. Nifty 50 stock list — for the UI dropdown
    // =======================================================================

    /**
     * Returns all 50 valid NSE stocks sorted by company name.
     * The UI uses this to populate the stock-name dropdown / autocomplete.
     */
    public List<NseStockInfo> getNiftyStockList() {
        logger.debug("Returning Nifty 50 stock list ({} stocks)", NseStocks.SYMBOLS.size());
        return NseStocks.SYMBOLS.stream()
                .map(symbol -> NseStockInfo.builder()
                        .symbol(symbol)
                        .displaySymbol(symbol.replace(".NS", ""))
                        .companyName(NseStocks.DISPLAY_NAMES.getOrDefault(symbol, symbol.replace(".NS", "")))
                        .build())
                .sorted(Comparator.comparing(NseStockInfo::getCompanyName))
                .toList();
    }

    // =======================================================================
    // B. Manual single-stock operations
    // =======================================================================

    /**
     * Adds a single stock to the user's portfolio.
     *
     * <ul>
     *   <li>Symbol is normalised and validated against the Nifty 50 list.</li>
     *   <li>If the stock is already in the portfolio a
     *       {@link StockAlreadyInPortfolioException} is thrown so the UI can
     *       redirect the user to the update form pre-filled with current values.</li>
     * </ul>
     */
    @Transactional
    public PortfolioResponse addSingleStock(AddStockRequest request, String username) {
        logger.info("User '{}' — add single stock request: symbol='{}', qty={}, price={}",
                username, request.getSymbol(), request.getQuantity(), request.getBuyingPrice());

        String symbol = resolveAndValidateSymbol(request.getSymbol());
        validateQuantityAndPrice(request.getQuantity(), request.getBuyingPrice(), symbol);

        User user = fetchUser(username);

        if (portfolioRepository.existsByUserAndSymbol(user, symbol)) {
            Portfolio existing = portfolioRepository.findByUserAndSymbol(user, symbol).orElseThrow();
            logger.warn("User '{}' — '{}' already in portfolio, returning conflict", username, symbol);
            throw new StockAlreadyInPortfolioException(
                    symbol.replace(".NS", "") + " is already in your portfolio. "
                    + "Use the update option to change quantity or buying price.",
                    toPortfolioResponse(existing));
        }

        Portfolio holding = Portfolio.builder()
                .user(user)
                .symbol(symbol)
                .companyName(NseStocks.DISPLAY_NAMES.getOrDefault(symbol, symbol.replace(".NS", "")))
                .quantity(request.getQuantity())
                .buyingPrice(request.getBuyingPrice())
                .build();

        Portfolio saved = portfolioRepository.save(holding);
        logger.info("User '{}' — added '{}': qty={}, buyingPrice={}",
                username, symbol, saved.getQuantity(), saved.getBuyingPrice());

        return toPortfolioResponse(saved);
    }

    /**
     * Updates quantity and/or buying price of an existing stock holding.
     *
     * @param rawSymbol path variable from the URL (e.g. "RELIANCE" or "RELIANCE.NS")
     */
    @Transactional
    public PortfolioResponse updateHolding(String rawSymbol, AddStockRequest request, String username) {
        String symbol = resolveAndValidateSymbol(rawSymbol);
        validateQuantityAndPrice(request.getQuantity(), request.getBuyingPrice(), symbol);

        logger.info("User '{}' — update '{}': qty={}, price={}",
                username, symbol, request.getQuantity(), request.getBuyingPrice());

        User user = fetchUser(username);

        Portfolio existing = portfolioRepository.findByUserAndSymbol(user, symbol)
                .orElseThrow(() -> {
                    logger.warn("User '{}' — update failed: '{}' not in portfolio", username, symbol);
                    return new IllegalArgumentException(
                            symbol.replace(".NS", "") + " is not in your portfolio.");
                });

        int        oldQty   = existing.getQuantity();
        BigDecimal oldPrice = existing.getBuyingPrice();

        existing.setQuantity(request.getQuantity());
        existing.setBuyingPrice(request.getBuyingPrice());
        Portfolio updated = portfolioRepository.save(existing);

        logger.info("User '{}' — updated '{}': qty {} → {}, price {} → {}",
                username, symbol, oldQty, updated.getQuantity(), oldPrice, updated.getBuyingPrice());

        return toPortfolioResponse(updated);
    }

    /**
     * Removes a stock holding from the user's portfolio.
     *
     * @param rawSymbol path variable from the URL
     */
    @Transactional
    public void removeHolding(String rawSymbol, String username) {
        String symbol = resolveAndValidateSymbol(rawSymbol);

        logger.info("User '{}' — remove '{}' from portfolio", username, symbol);

        User user = fetchUser(username);

        Portfolio existing = portfolioRepository.findByUserAndSymbol(user, symbol)
                .orElseThrow(() -> {
                    logger.warn("User '{}' — remove failed: '{}' not in portfolio", username, symbol);
                    return new IllegalArgumentException(
                            symbol.replace(".NS", "") + " is not in your portfolio.");
                });

        portfolioRepository.delete(existing);
        logger.info("User '{}' — removed '{}' successfully", username, symbol);
    }

    // =======================================================================
    // C. View portfolio
    // =======================================================================

    @Transactional(readOnly = true)
    public List<PortfolioResponse> getUserPortfolio(String username) {
        User user = fetchUser(username);
        logger.info("User '{}' — fetching portfolio", username);
        List<PortfolioResponse> portfolio = portfolioRepository.findByUserOrderBySymbolAsc(user)
                .stream()
                .map(this::toPortfolioResponse)
                .toList();
        logger.info("User '{}' — portfolio: {} holding(s)", username, portfolio.size());
        return portfolio;
    }

    // =======================================================================
    // D. Portfolio valuation  (buying price + live/cached market price → P&L)
    // =======================================================================

    /**
     * Returns a full portfolio valuation by combining each holding's buying-price
     * data with the latest market price available in the {@link StockService} cache.
     *
     * <p>If no market price data is available yet the currentPrice / currentValue
     * fields will be {@code 0} and dataStatus will be "UNAVAILABLE".</p>
     */
    @Transactional(readOnly = true)
    public PortfolioValuationResponse getPortfolioValuation(String username) {
        logger.info("User '{}' — computing portfolio valuation", username);

        User user = fetchUser(username);
        List<Portfolio> holdings = portfolioRepository.findByUserOrderBySymbolAsc(user);

        // Grab a symbol → quote snapshot from the ticker cache (one map lookup per holding)
        Map<String, StockQuote> priceMap  = stockService.getCurrentQuotesMap();
        String                  dataStatus = stockService.getDataStatus();

        BigDecimal totalInvestment   = BigDecimal.ZERO;
        BigDecimal totalCurrentValue = BigDecimal.ZERO;

        List<HoldingValuation> valuations = new ArrayList<>();

        for (Portfolio p : holdings) {
            BigDecimal investmentValue = p.getBuyingPrice()
                    .multiply(BigDecimal.valueOf(p.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            StockQuote quote        = priceMap.get(p.getSymbol());
            BigDecimal currentPrice = (quote != null)
                    ? BigDecimal.valueOf(quote.getPrice()).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal currentValue = currentPrice
                    .multiply(BigDecimal.valueOf(p.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal profitLoss = currentValue.subtract(investmentValue);
            double plPercent = investmentValue.compareTo(BigDecimal.ZERO) > 0
                    ? profitLoss.divide(investmentValue, 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP)
                                .doubleValue()
                    : 0.0;

            totalInvestment   = totalInvestment.add(investmentValue);
            totalCurrentValue = totalCurrentValue.add(currentValue);

            valuations.add(HoldingValuation.builder()
                    .symbol(p.getSymbol())
                    .displaySymbol(p.getSymbol().replace(".NS", ""))
                    .companyName(p.getCompanyName())
                    .quantity(p.getQuantity())
                    .buyingPrice(p.getBuyingPrice())
                    .investmentValue(investmentValue)
                    .currentPrice(currentPrice)
                    .currentValue(currentValue)
                    .profitLoss(profitLoss.setScale(2, RoundingMode.HALF_UP))
                    .plPercent(plPercent)
                    .gain(profitLoss.compareTo(BigDecimal.ZERO) >= 0)
                    .marketState(quote != null ? quote.getMarketState() : "UNKNOWN")
                    .build());
        }

        BigDecimal totalProfitLoss = totalCurrentValue.subtract(totalInvestment);
        double totalPLPercent = totalInvestment.compareTo(BigDecimal.ZERO) > 0
                ? totalProfitLoss.divide(totalInvestment, 6, RoundingMode.HALF_UP)
                                 .multiply(BigDecimal.valueOf(100))
                                 .setScale(2, RoundingMode.HALF_UP)
                                 .doubleValue()
                : 0.0;

        logger.info("User '{}' — valuation: {} holdings, invested=₹{}, currentValue=₹{}, P&L=₹{} ({}%), dataStatus={}",
                username, valuations.size(),
                totalInvestment, totalCurrentValue,
                totalProfitLoss, totalPLPercent, dataStatus);

        return PortfolioValuationResponse.builder()
                .holdings(valuations)
                .totalHoldings(valuations.size())
                .totalInvestment(totalInvestment.setScale(2, RoundingMode.HALF_UP))
                .totalCurrentValue(totalCurrentValue.setScale(2, RoundingMode.HALF_UP))
                .totalProfitLoss(totalProfitLoss.setScale(2, RoundingMode.HALF_UP))
                .totalPLPercent(totalPLPercent)
                .dataStatus(dataStatus)
                .valuedAt(LocalDateTime.now())
                .build();
    }

    // =======================================================================
    // E. Excel bulk upload (unchanged)
    // =======================================================================

    @Transactional(readOnly = true)
    public PortfolioUploadPreview previewUpload(MultipartFile file, String username) {
        logger.info("Portfolio upload initiated by user '{}' — file: '{}'",
                username, file.getOriginalFilename());

        User user = fetchUser(username);

        ParseResult parsed = excelParser.parse(file);
        logger.info("Excel parsing done for user '{}': {} valid row(s), {} parse error(s)",
                username, parsed.entries().size(), parsed.parseErrors().size());

        Map<String, Portfolio> existingBySymbol = portfolioRepository
                .findByUserOrderBySymbolAsc(user)
                .stream()
                .collect(Collectors.toMap(Portfolio::getSymbol, p -> p));

        List<PortfolioEntry>      newStocks      = new ArrayList<>();
        List<PortfolioUpdateItem> stocksToUpdate = new ArrayList<>();
        List<String>              invalidSymbols = new ArrayList<>();

        for (PortfolioEntry rawEntry : parsed.entries()) {
            String normalizedSymbol = normalizeSymbol(rawEntry.getSymbol());

            if (normalizedSymbol == null) {
                invalidSymbols.add(rawEntry.getSymbol());
                logger.warn("User '{}' uploaded unrecognised symbol '{}'", username, rawEntry.getSymbol());
                continue;
            }

            String displaySymbol = normalizedSymbol.replace(".NS", "");
            String companyName   = NseStocks.DISPLAY_NAMES.getOrDefault(normalizedSymbol, displaySymbol);

            PortfolioEntry entry = PortfolioEntry.builder()
                    .symbol(normalizedSymbol)
                    .displaySymbol(displaySymbol)
                    .companyName(companyName)
                    .quantity(rawEntry.getQuantity())
                    .buyingPrice(rawEntry.getBuyingPrice())
                    .build();

            if (existingBySymbol.containsKey(normalizedSymbol)) {
                Portfolio current = existingBySymbol.get(normalizedSymbol);
                stocksToUpdate.add(buildUpdateItem(entry, current));
                logger.info("User '{}': '{}' already in portfolio — will update (qty {} → {}, price {} → {})",
                        username, normalizedSymbol,
                        current.getQuantity(), entry.getQuantity(),
                        current.getBuyingPrice(), entry.getBuyingPrice());
            } else {
                newStocks.add(entry);
                logger.info("User '{}': '{}' is a new holding — will add", username, normalizedSymbol);
            }
        }

        String userMessage = buildUploadUserMessage(
                newStocks.size(), stocksToUpdate.size(), invalidSymbols.size(), parsed.parseErrors().size());

        logger.info("Preview ready for user '{}': {} new, {} to update, {} invalid, {} parse errors",
                username, newStocks.size(), stocksToUpdate.size(), invalidSymbols.size(), parsed.parseErrors().size());

        return PortfolioUploadPreview.builder()
                .newStocks(newStocks)
                .stocksToUpdate(stocksToUpdate)
                .invalidSymbols(invalidSymbols)
                .parseErrors(parsed.parseErrors())
                .userMessage(userMessage)
                .requiresConfirmation(!stocksToUpdate.isEmpty())
                .build();
    }

    @Transactional
    public PortfolioConfirmResponse confirmUpload(PortfolioConfirmRequest request, String username) {
        logger.info("Portfolio confirm by user '{}': {} to add, {} to update",
                username, request.getToAdd().size(), request.getToUpdate().size());

        User user = fetchUser(username);
        int addedCount = 0, updatedCount = 0, skippedCount = 0;

        for (PortfolioEntry entry : request.getToAdd()) {
            String symbol = normalizeSymbol(entry.getSymbol());
            if (symbol == null) { skippedCount++; continue; }
            if (portfolioRepository.existsByUserAndSymbol(user, symbol)) { skippedCount++; continue; }

            portfolioRepository.save(Portfolio.builder()
                    .user(user)
                    .symbol(symbol)
                    .companyName(NseStocks.DISPLAY_NAMES.getOrDefault(symbol, symbol.replace(".NS", "")))
                    .quantity(entry.getQuantity())
                    .buyingPrice(entry.getBuyingPrice())
                    .build());
            addedCount++;
            logger.info("User '{}': added '{}' — qty={}, price={}", username, symbol, entry.getQuantity(), entry.getBuyingPrice());
        }

        for (PortfolioEntry entry : request.getToUpdate()) {
            String symbol = normalizeSymbol(entry.getSymbol());
            if (symbol == null) { skippedCount++; continue; }

            Optional<Portfolio> opt = portfolioRepository.findByUserAndSymbol(user, symbol);
            if (opt.isEmpty()) { skippedCount++; continue; }

            Portfolio existing = opt.get();
            existing.setQuantity(entry.getQuantity());
            existing.setBuyingPrice(entry.getBuyingPrice());
            portfolioRepository.save(existing);
            updatedCount++;
            logger.info("User '{}': updated '{}' — qty={}, price={}", username, symbol, entry.getQuantity(), entry.getBuyingPrice());
        }

        String message = buildConfirmMessage(addedCount, updatedCount, skippedCount);
        logger.info("Confirm complete for user '{}': {}", username, message);

        return PortfolioConfirmResponse.builder()
                .addedCount(addedCount)
                .updatedCount(updatedCount)
                .skippedCount(skippedCount)
                .message(message)
                .portfolio(getUserPortfolio(username))
                .build();
    }

    // =======================================================================
    // Internals
    // =======================================================================

    /**
     * Normalises the user-supplied stock identifier to a canonical Yahoo Finance
     * symbol (e.g. "RELIANCE.NS").  Returns {@code null} if the identifier does
     * not match any Nifty 50 stock.
     *
     * Accepted input forms:
     * <ul>
     *   <li>"RELIANCE"            — bare ticker (adds .NS suffix)</li>
     *   <li>"RELIANCE.NS"         — already qualified</li>
     *   <li>"Reliance Industries" — display name (case-insensitive)</li>
     * </ul>
     */
    private String normalizeSymbol(String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();
        String upper   = trimmed.toUpperCase();

        if (upper.endsWith(".NS")) {
            return NseStocks.SYMBOLS.contains(upper) ? upper : null;
        }

        String withNs = upper + ".NS";
        if (NseStocks.SYMBOLS.contains(withNs)) return withNs;

        for (Map.Entry<String, String> entry : NseStocks.DISPLAY_NAMES.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(trimmed)) return entry.getKey();
        }
        return null;
    }

    /** Normalises the symbol and throws {@link IllegalArgumentException} if invalid. */
    private String resolveAndValidateSymbol(String input) {
        String symbol = normalizeSymbol(input);
        if (symbol == null) {
            throw new IllegalArgumentException(
                    "'" + input + "' is not a valid Nifty 50 stock. "
                    + "Use GET /api/portfolio/stocks to see the valid list.");
        }
        return symbol;
    }

    private void validateQuantityAndPrice(Integer qty, BigDecimal price, String symbol) {
        if (qty == null || qty <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be a positive integer for " + symbol.replace(".NS", ""));
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Buying price must be a positive value for " + symbol.replace(".NS", ""));
        }
    }

    private User fetchUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logger.error("Authenticated user '{}' not found in DB", username);
                    return new IllegalStateException("Authenticated user not found: " + username);
                });
    }

    private PortfolioUpdateItem buildUpdateItem(PortfolioEntry incoming, Portfolio current) {
        String desc = String.format(
                "Quantity: %d → %d  |  Buying Price: ₹%.2f → ₹%.2f",
                current.getQuantity(), incoming.getQuantity(),
                current.getBuyingPrice(), incoming.getBuyingPrice());
        return PortfolioUpdateItem.builder()
                .symbol(incoming.getSymbol())
                .displaySymbol(incoming.getDisplaySymbol())
                .companyName(incoming.getCompanyName())
                .currentQuantity(current.getQuantity())
                .currentBuyingPrice(current.getBuyingPrice())
                .newQuantity(incoming.getQuantity())
                .newBuyingPrice(incoming.getBuyingPrice())
                .changeDescription(desc)
                .build();
    }

    private PortfolioResponse toPortfolioResponse(Portfolio p) {
        BigDecimal total = p.getBuyingPrice()
                .multiply(BigDecimal.valueOf(p.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        return PortfolioResponse.builder()
                .id(p.getId())
                .symbol(p.getSymbol())
                .displaySymbol(p.getSymbol().replace(".NS", ""))
                .companyName(p.getCompanyName())
                .quantity(p.getQuantity())
                .buyingPrice(p.getBuyingPrice())
                .totalInvestment(total)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private String buildUploadUserMessage(int newCount, int updateCount, int invalidCount, int errorCount) {
        StringBuilder sb = new StringBuilder();
        if (newCount    > 0) sb.append(newCount).append(newCount == 1 ? " new stock" : " new stocks").append(" will be added. ");
        if (updateCount > 0) sb.append(updateCount).append(updateCount == 1 ? " existing stock" : " existing stocks").append(" will be updated — please review and confirm. ");
        if (invalidCount > 0) sb.append(invalidCount).append(invalidCount == 1 ? " symbol" : " symbols").append(" not recognised as Nifty 50 stocks and will be skipped. ");
        if (errorCount  > 0) sb.append(errorCount).append(errorCount == 1 ? " row" : " rows").append(" could not be parsed and will be skipped.");
        if (sb.isEmpty()) sb.append("No valid portfolio data found in the file.");
        return sb.toString().trim();
    }

    private String buildConfirmMessage(int added, int updated, int skipped) {
        StringBuilder sb = new StringBuilder("Portfolio updated successfully. ");
        if (added   > 0) sb.append(added).append(added == 1 ? " stock added. " : " stocks added. ");
        if (updated > 0) sb.append(updated).append(updated == 1 ? " stock updated. " : " stocks updated. ");
        if (skipped > 0) sb.append(skipped).append(skipped == 1 ? " entry skipped." : " entries skipped.");
        return sb.toString().trim();
    }
}
