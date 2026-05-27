package com.practice.demo.controller;

import com.practice.demo.dto.*;
import com.practice.demo.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(
    name        = "Portfolio",
    description = "Manage stock holdings — manual entry, Excel bulk upload, and P&L valuation. "
                + "All endpoints require a valid JWT."
)
@SecurityRequirement(name = "bearerAuth")
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

    @Operation(
        summary     = "List valid Nifty 50 stocks",
        description = "Returns all 50 NSE stocks sorted alphabetically by company name. "
                    + "Use this list to populate the stock-name dropdown / autocomplete in the UI "
                    + "so that only valid Nifty 50 symbols can be submitted."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description  = "List of 50 stocks returned",
            content      = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                array     = @ArraySchema(schema = @Schema(implementation = NseStockInfo.class))
            )
        ),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
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

    @Operation(
        summary     = "Add a stock holding",
        description = "Adds a new stock to the authenticated user's portfolio. "
                    + "`symbol` accepts bare ticker (`RELIANCE`), qualified form (`RELIANCE.NS`), "
                    + "or company display name (case-insensitive). "
                    + "Returns **409 Conflict** if the stock is already in the portfolio, "
                    + "with the existing record in the response body so the UI can pre-fill the update form."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description  = "Holding added successfully",
            content      = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema    = @Schema(implementation = PortfolioResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description  = "Invalid symbol, quantity ≤ 0, or buying price ≤ 0",
            content      = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "401",
            description  = "Missing or invalid JWT",
            content      = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "409",
            description  = "Stock already in portfolio — response body contains the existing holding",
            content      = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema    = @Schema(implementation = PortfolioResponse.class)
            )
        )
    })
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

    @Operation(
        summary     = "Update a holding's quantity / buying price",
        description = "Overwrites the quantity and buying price of an existing stock holding. "
                    + "The path `{symbol}` accepts bare ticker, qualified form, or display name."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description  = "Holding updated",
            content      = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema    = @Schema(implementation = PortfolioResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description  = "Symbol not in portfolio, or validation failure",
            content      = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    @PutMapping("/{symbol}")
    public ResponseEntity<PortfolioResponse> updateHolding(
            @Parameter(description = "Stock ticker or company name", example = "RELIANCE")
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

    @Operation(
        summary     = "Remove a holding from portfolio",
        description = "Deletes the specified stock holding. The path `{symbol}` accepts "
                    + "bare ticker, qualified form, or display name."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Holding deleted"),
        @ApiResponse(
            responseCode = "400",
            description  = "Symbol not found in portfolio",
            content      = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> removeHolding(
            @Parameter(description = "Stock ticker or company name", example = "INFY")
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

    @Operation(
        summary     = "Get portfolio P&L valuation",
        description = "Combines each holding's buying-price data with the latest market price "
                    + "from the ticker cache to compute per-stock and overall P&L. "
                    + "`dataStatus` reflects data quality: `LIVE`, `CACHED`, or `UNAVAILABLE`."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description  = "Valuation computed",
            content      = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema    = @Schema(implementation = PortfolioValuationResponse.class)
            )
        ),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
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

    @Operation(
        summary     = "Upload portfolio Excel — Step 1: Preview",
        description = "Accepts a `.xls` or `.xlsx` portfolio file, parses it, validates each "
                    + "stock name against the Nifty 50 list, and compares with the user's current "
                    + "portfolio. **No data is written.** The response shows: new stocks to add, "
                    + "existing stocks to update (before/after), and unrecognised symbols to skip. "
                    + "Call `POST /confirm` after the user approves the preview."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description  = "Preview generated",
            content      = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema    = @Schema(implementation = PortfolioUploadPreview.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description  = "File is empty, unreadable, or not a valid Excel format",
            content      = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PortfolioUploadPreview> uploadPortfolio(
            @Parameter(description = "Excel file (.xls or .xlsx) with columns: Symbol, Quantity, Buying Price")
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

    @Operation(
        summary     = "Upload portfolio Excel — Step 2: Confirm",
        description = "Applies the changes the user approved in the preview step. "
                    + "Pass back the `newStocks` and `stocksToUpdate` from the preview response. "
                    + "Every symbol is re-validated server-side before writing."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description  = "Changes applied; response includes add/update/skip counts and the updated portfolio",
            content      = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema    = @Schema(implementation = PortfolioConfirmResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description  = "Payload is malformed or contains an invalid symbol",
            content      = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
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

    @Operation(
        summary     = "Get current holdings (no market prices)",
        description = "Returns the authenticated user's holdings sorted alphabetically by symbol. "
                    + "Does **not** include live market prices or P&L. "
                    + "For a full valuation use `GET /api/portfolio/valuation`."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description  = "Holdings returned (may be an empty list if portfolio is empty)",
            content      = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                array     = @ArraySchema(schema = @Schema(implementation = PortfolioResponse.class))
            )
        ),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
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
