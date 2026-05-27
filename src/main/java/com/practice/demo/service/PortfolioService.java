package com.practice.demo.service;

import com.practice.demo.constants.NseStocks;
import com.practice.demo.dto.*;
import com.practice.demo.entity.Portfolio;
import com.practice.demo.entity.User;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PortfolioService {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioService.class);

    private final PortfolioRepository portfolioRepository;
    private final UserRepository      userRepository;
    private final ExcelParser         excelParser;

    public PortfolioService(PortfolioRepository portfolioRepository,
                            UserRepository userRepository,
                            ExcelParser excelParser) {
        this.portfolioRepository = portfolioRepository;
        this.userRepository      = userRepository;
        this.excelParser         = excelParser;
    }

    // -----------------------------------------------------------------------
    // Step 1 — Upload & Preview  (no DB write)
    // -----------------------------------------------------------------------

    /**
     * Parses the uploaded Excel file, validates each stock name against the
     * Nifty 50 list, and compares the result with the user's existing portfolio.
     *
     * Returns a preview that the UI must show to the user before confirming.
     * Nothing is written to the database at this stage.
     */
    @Transactional(readOnly = true)
    public PortfolioUploadPreview previewUpload(MultipartFile file, String username) {
        logger.info("Portfolio upload initiated by user '{}' — file: '{}'",
                username, file.getOriginalFilename());

        User user = fetchUser(username);

        // ── Parse Excel ──────────────────────────────────────────────────────
        ParseResult parsed = excelParser.parse(file);
        logger.info("Excel parsing done for user '{}': {} valid row(s), {} parse error(s)",
                username, parsed.entries().size(), parsed.parseErrors().size());

        // ── Load existing portfolio as symbol → entity map ───────────────────
        Map<String, Portfolio> existingBySymbol = portfolioRepository
                .findByUserOrderBySymbolAsc(user)
                .stream()
                .collect(Collectors.toMap(Portfolio::getSymbol, p -> p));

        // ── Classify each parsed row ─────────────────────────────────────────
        List<PortfolioEntry>    newStocks      = new ArrayList<>();
        List<PortfolioUpdateItem> stocksToUpdate = new ArrayList<>();
        List<String>            invalidSymbols = new ArrayList<>();

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
                // Stock already in portfolio — build a diff item for the user to review
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

        String userMessage = buildUserMessage(
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

    // -----------------------------------------------------------------------
    // Step 2 — Confirm & Persist  (DB write)
    // -----------------------------------------------------------------------

    /**
     * Applies the changes the user has confirmed.
     * Re-validates every symbol before writing to prevent stale/tampered data.
     */
    @Transactional
    public PortfolioConfirmResponse confirmUpload(PortfolioConfirmRequest request, String username) {
        logger.info("Portfolio confirm by user '{}': {} to add, {} to update",
                username, request.getToAdd().size(), request.getToUpdate().size());

        User user = fetchUser(username);

        int addedCount   = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        // ── Add new stocks ───────────────────────────────────────────────────
        for (PortfolioEntry entry : request.getToAdd()) {
            String symbol = normalizeSymbol(entry.getSymbol());
            if (symbol == null) {
                logger.warn("User '{}': skipping invalid symbol '{}' during confirm (add)",
                        username, entry.getSymbol());
                skippedCount++;
                continue;
            }
            if (portfolioRepository.existsByUserAndSymbol(user, symbol)) {
                logger.warn("User '{}': '{}' already exists — skipping duplicate add", username, symbol);
                skippedCount++;
                continue;
            }

            Portfolio holding = Portfolio.builder()
                    .user(user)
                    .symbol(symbol)
                    .companyName(NseStocks.DISPLAY_NAMES.getOrDefault(symbol, symbol.replace(".NS", "")))
                    .quantity(entry.getQuantity())
                    .buyingPrice(entry.getBuyingPrice())
                    .build();

            portfolioRepository.save(holding);
            addedCount++;
            logger.info("User '{}': added '{}' — qty={}, price={}",
                    username, symbol, entry.getQuantity(), entry.getBuyingPrice());
        }

        // ── Update existing stocks ───────────────────────────────────────────
        for (PortfolioEntry entry : request.getToUpdate()) {
            String symbol = normalizeSymbol(entry.getSymbol());
            if (symbol == null) {
                logger.warn("User '{}': skipping invalid symbol '{}' during confirm (update)",
                        username, entry.getSymbol());
                skippedCount++;
                continue;
            }

            Optional<Portfolio> existingOpt = portfolioRepository.findByUserAndSymbol(user, symbol);
            if (existingOpt.isEmpty()) {
                logger.warn("User '{}': '{}' not found in portfolio for update — skipping", username, symbol);
                skippedCount++;
                continue;
            }

            Portfolio existing = existingOpt.get();
            int    oldQty   = existing.getQuantity();
            BigDecimal oldPrice = existing.getBuyingPrice();

            existing.setQuantity(entry.getQuantity());
            existing.setBuyingPrice(entry.getBuyingPrice());
            portfolioRepository.save(existing);
            updatedCount++;

            logger.info("User '{}': updated '{}' — qty {} → {}, price {} → {}",
                    username, symbol, oldQty, entry.getQuantity(), oldPrice, entry.getBuyingPrice());
        }

        String message = buildConfirmMessage(addedCount, updatedCount, skippedCount);
        logger.info("Portfolio confirm complete for user '{}': {}", username, message);

        List<PortfolioResponse> updatedPortfolio = getUserPortfolio(username);

        return PortfolioConfirmResponse.builder()
                .addedCount(addedCount)
                .updatedCount(updatedCount)
                .skippedCount(skippedCount)
                .message(message)
                .portfolio(updatedPortfolio)
                .build();
    }

    // -----------------------------------------------------------------------
    // GET portfolio
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PortfolioResponse> getUserPortfolio(String username) {
        User user = fetchUser(username);
        logger.info("Fetching portfolio for user '{}'", username);
        List<PortfolioResponse> portfolio = portfolioRepository.findByUserOrderBySymbolAsc(user)
                .stream()
                .map(this::toPortfolioResponse)
                .toList();
        logger.info("User '{}' portfolio: {} holding(s)", username, portfolio.size());
        return portfolio;
    }

    // -----------------------------------------------------------------------
    // Symbol normalisation & validation
    // -----------------------------------------------------------------------

    /**
     * Accepts any of the following input forms and returns a canonical
     * Yahoo Finance symbol (e.g. "RELIANCE.NS"), or {@code null} if the
     * stock is not part of the Nifty 50 list.
     *
     * Supported input forms:
     *   "RELIANCE"            → "RELIANCE.NS"   (bare ticker)
     *   "RELIANCE.NS"         → "RELIANCE.NS"   (already qualified)
     *   "Reliance Industries" → "RELIANCE.NS"   (display name, case-insensitive)
     */
    private String normalizeSymbol(String input) {
        if (input == null || input.isBlank()) return null;

        String trimmed = input.trim();
        String upper   = trimmed.toUpperCase();

        // Already a qualified symbol?
        if (upper.endsWith(".NS")) {
            return NseStocks.SYMBOLS.contains(upper) ? upper : null;
        }

        // Try bare ticker + ".NS"
        String withNs = upper + ".NS";
        if (NseStocks.SYMBOLS.contains(withNs)) return withNs;

        // Try matching a curated display name (case-insensitive)
        for (Map.Entry<String, String> entry : NseStocks.DISPLAY_NAMES.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(trimmed)) return entry.getKey();
        }

        return null; // not a valid Nifty 50 stock
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private User fetchUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logger.error("Authenticated user '{}' not found in database", username);
                    return new IllegalStateException("Authenticated user not found: " + username);
                });
    }

    private PortfolioUpdateItem buildUpdateItem(PortfolioEntry incoming, Portfolio current) {
        String desc = String.format(
                "Quantity: %d → %d | Buying Price: ₹%.2f → ₹%.2f",
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
                .multiply(BigDecimal.valueOf(p.getQuantity()));
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

    private String buildUserMessage(int newCount, int updateCount, int invalidCount, int errorCount) {
        StringBuilder sb = new StringBuilder();
        if (newCount    > 0) sb.append(newCount).append(newCount == 1 ? " new stock" : " new stocks").append(" will be added. ");
        if (updateCount > 0) sb.append(updateCount).append(updateCount == 1 ? " existing stock" : " existing stocks").append(" will be updated — please review the changes below and confirm. ");
        if (invalidCount > 0) sb.append(invalidCount).append(invalidCount == 1 ? " symbol was" : " symbols were").append(" not recognised as valid Nifty 50 stocks and will be skipped. ");
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
