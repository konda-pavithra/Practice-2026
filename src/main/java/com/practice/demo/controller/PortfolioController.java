package com.practice.demo.controller;

import com.practice.demo.dto.*;
import com.practice.demo.service.PortfolioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Portfolio management endpoints — all require a valid JWT.
 *
 * ┌──────────────────────────────────────┬────────────────────────────────────────────┐
 * │ Endpoint                             │ Purpose                                    │
 * ├──────────────────────────────────────┼────────────────────────────────────────────┤
 * │ GET    /api/portfolio/stocks         │ Nifty 50 stock list for the UI dropdown    │
 * │ POST   /api/portfolio/add            │ Add a single stock holding manually        │
 * │ PUT    /api/portfolio/{symbol}       │ Update quantity / buying price of holding  │
 * │ DELETE /api/portfolio/{symbol}       │ Remove a holding from portfolio            │
 * │ GET    /api/portfolio/valuation      │ Full P&L valuation with live/cached prices │
 * ├──────────────────────────────────────┼────────────────────────────────────────────┤
 * │ POST   /api/portfolio/upload         │ Parse .xls/.xlsx, return a preview diff    │
 * │ POST   /api/portfolio/confirm        │ Apply confirmed Excel changes to database  │
 * │ GET    /api/portfolio                │ Current holdings (raw, no market price)    │
 * └──────────────────────────────────────┴────────────────────────────────────────────┘
 *
 * Manual-entry flow:
 *   1. UI fetches GET /stocks → populates dropdown with valid Nifty 50 names.
 *   2. User picks a stock, enters quantity + buying price → POST /add.
 *      • 201 Created  — holding saved.
 *      • 409 Conflict — stock already in portfolio; body contains existing values
 *                       so the UI can redirect to the update form pre-filled.
 *   3. User edits an existing holding → PUT /{symbol}.
 *   4. User removes a holding → DELETE /{symbol}.
 *   5. UI fetches GET /valuation → shows total P&L using live/cached market prices.
 *
 * Excel-upload flow (two-step):
 *   POST /upload → preview (no DB write) → POST /confirm → DB write.
 */
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioController.class);

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    // =========================================================================
    // Manual entry — stock list for dropdown
    // =========================================================================

    /**
     * Returns all 50 valid NSE stocks sorted alphabetically by company name.
     * The UI uses this list to populate the stock-name dropdown / autocomplete
     * so that only valid Nifty 50 stocks can be submitted.
     *
     * <b>Response: 200 OK — List&lt;NseStockInfo&gt;</b>
     * Each item: { symbol, displaySymbol, companyName }
     */
    @GetMapping("/stocks")
    public ResponseEntity<List<NseStockInfo>> getNiftyStockList(Authentication authentication) {
        logger.info("GET /api/portfolio/stocks — user '{}'", authentication.getName());
        List<NseStockInfo> stocks = portfolioService.getNiftyStockList();
        logger.debug("GET /api/portfolio/stocks — returning {} stocks", stocks.size());
        return ResponseEntity.ok(stocks);
    }

    // =========================================================================
    // Manual entry — add a single holding
    // =========================================================================

    /**
     * Adds a new stock holding to the authenticated user's portfolio.
     *
     * <p>Request body:
     * <pre>{ "symbol": "RELIANCE", "quantity": 10, "buyingPrice": 2450.50 }</pre>
     * {@code symbol} accepts bare ticker ("RELIANCE"), qualified ("RELIANCE.NS"),
     * or company display name ("Reliance Industries Limited") — all normalised
     * server-side.
     *
     * <p>Responses:
     * <ul>
     *   <li>201 Created  — new holding persisted; body is the saved PortfolioResponse.</li>
     *   <li>409 Conflict — stock already in portfolio; body contains the existing
     *       holding so the UI can pre-fill the update form.</li>
     *   <li>400 Bad Request — invalid symbol, quantity ≤ 0, or buying price ≤ 0.</li>
     * </ul>
     */
    @PostMapping("/add")
    public ResponseEntity<PortfolioResponse> addStock(
            @RequestBody AddStockRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("POST /api/portfolio/add — user '{}': symbol='{}', qty={}, price={}",
                username, request.getSymbol(), request.getQuantity(), request.getBuyingPrice());

        PortfolioResponse response = portfolioService.addSingleStock(request, username);

        logger.info("POST /api/portfolio/add — user '{}': '{}' added successfully",
                username, response.getSymbol());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================================
    // Manual entry — update an existing holding
    // =========================================================================

    /**
     * Updates the quantity and/or buying price of an existing stock holding.
     *
     * <p>Path variable {@code symbol} accepts bare ticker or qualified form.
     *
     * <p>Responses:
     * <ul>
     *   <li>200 OK         — holding updated; body is the updated PortfolioResponse.</li>
     *   <li>400 Bad Request — symbol not in portfolio, or validation failure.</li>
     * </ul>
     */
    @PutMapping("/{symbol}")
    public ResponseEntity<PortfolioResponse> updateHolding(
            @PathVariable String symbol,
            @RequestBody AddStockRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("PUT /api/portfolio/{} — user '{}': qty={}, price={}",
                symbol, username, request.getQuantity(), request.getBuyingPrice());

        PortfolioResponse response = portfolioService.updateHolding(symbol, request, username);

        logger.info("PUT /api/portfolio/{} — user '{}': updated successfully", symbol, username);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Manual entry — remove a holding
    // =========================================================================

    /**
     * Removes the specified stock holding from the user's portfolio.
     *
     * <p>Responses:
     * <ul>
     *   <li>204 No Content — holding deleted.</li>
     *   <li>400 Bad Request — symbol not found in portfolio.</li>
     * </ul>
     */
    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> removeHolding(
            @PathVariable String symbol,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("DELETE /api/portfolio/{} — user '{}'", symbol, username);

        portfolioService.removeHolding(symbol, username);

        logger.info("DELETE /api/portfolio/{} — user '{}': removed successfully", symbol, username);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Portfolio valuation — buying price + live market prices → P&L
    // =========================================================================

    /**
     * Returns a full portfolio valuation by combining each holding's buying-price
     * data with the latest market price available in the stock ticker cache.
     *
     * <p>Fields per holding: symbol, companyName, quantity, buyingPrice,
     * investmentValue, currentPrice, currentValue, profitLoss, plPercent, gain,
     * marketState.
     *
     * <p>Portfolio-level aggregates: totalInvestment, totalCurrentValue,
     * totalProfitLoss, totalPLPercent.
     *
     * <p>{@code dataStatus} indicates data quality:
     * <ul>
     *   <li>"LIVE"        — prices from the latest Yahoo Finance fetch</li>
     *   <li>"CACHED"      — prices from a previous fetch (Yahoo Finance unavailable)</li>
     *   <li>"UNAVAILABLE" — no market data yet; P&amp;L figures will show ₹0</li>
     * </ul>
     */
    @GetMapping("/valuation")
    public ResponseEntity<PortfolioValuationResponse> getValuation(Authentication authentication) {
        String username = authentication.getName();
        logger.info("GET /api/portfolio/valuation — user '{}'", username);

        PortfolioValuationResponse valuation = portfolioService.getPortfolioValuation(username);

        logger.info("GET /api/portfolio/valuation — user '{}': {} holdings, invested=₹{}, current=₹{}, P&L=₹{} ({}%), dataStatus={}",
                username,
                valuation.getTotalHoldings(),
                valuation.getTotalInvestment(),
                valuation.getTotalCurrentValue(),
                valuation.getTotalProfitLoss(),
                valuation.getTotalPLPercent(),
                valuation.getDataStatus());

        return ResponseEntity.ok(valuation);
    }

    // =========================================================================
    // Excel bulk upload — Step 1: Preview (no DB write)
    // =========================================================================

    /**
     * Accepts a .xls or .xlsx portfolio file, parses it, validates every
     * stock name against the Nifty 50 list, and compares the result with
     * the user's existing portfolio.
     *
     * <b>No data is written to the database.</b>
     * The response shows:
     * <ul>
     *   <li>New stocks that will be added</li>
     *   <li>Existing stocks that will be updated (with before/after values)</li>
     *   <li>Unrecognised symbols that will be skipped</li>
     * </ul>
     * The UI must display this to the user and call {@code /confirm} if they agree.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PortfolioUploadPreview> uploadPortfolio(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("POST /api/portfolio/upload — user '{}', file '{}' ({} bytes)",
                username, file.getOriginalFilename(), file.getSize());

        PortfolioUploadPreview preview = portfolioService.previewUpload(file, username);

        logger.info("POST /api/portfolio/upload — preview sent to user '{}': {} new, {} updates, {} invalid",
                username,
                preview.getNewStocks().size(),
                preview.getStocksToUpdate().size(),
                preview.getInvalidSymbols().size());

        return ResponseEntity.ok(preview);
    }

    // =========================================================================
    // Excel bulk upload — Step 2: Confirm & Persist
    // =========================================================================

    /**
     * Applies the changes the user approved in the preview step.
     *
     * The client echoes back the {@code newStocks} it wants to add and the
     * {@code stocksToUpdate} it accepted. Every symbol is re-validated
     * server-side before writing, so stale or tampered payloads are rejected.
     *
     * Returns the updated portfolio alongside add/update/skip counts.
     */
    @PostMapping("/confirm")
    public ResponseEntity<PortfolioConfirmResponse> confirmPortfolio(
            @RequestBody PortfolioConfirmRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("POST /api/portfolio/confirm — user '{}': {} to add, {} to update",
                username, request.getToAdd().size(), request.getToUpdate().size());

        PortfolioConfirmResponse response = portfolioService.confirmUpload(request, username);

        logger.info("POST /api/portfolio/confirm — user '{}': {} added, {} updated, {} skipped",
                username, response.getAddedCount(), response.getUpdatedCount(), response.getSkippedCount());

        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // View raw holdings (no market price enrichment)
    // =========================================================================

    /**
     * Returns the authenticated user's current portfolio holdings sorted
     * alphabetically by symbol. Does not include live market prices or P&amp;L.
     * For a full valuation use {@code GET /api/portfolio/valuation}.
     */
    @GetMapping
    public ResponseEntity<List<PortfolioResponse>> getPortfolio(Authentication authentication) {
        String username = authentication.getName();
        logger.info("GET /api/portfolio — user '{}'", username);
        List<PortfolioResponse> portfolio = portfolioService.getUserPortfolio(username);
        logger.info("GET /api/portfolio — returning {} holding(s) for user '{}'",
                portfolio.size(), username);
        return ResponseEntity.ok(portfolio);
    }
}
