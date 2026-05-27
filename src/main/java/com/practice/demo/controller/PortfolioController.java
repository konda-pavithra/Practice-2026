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
 * ┌─────────────────────────────────┬──────────────────────────────────────────┐
 * │ Endpoint                        │ Purpose                                  │
 * ├─────────────────────────────────┼──────────────────────────────────────────┤
 * │ POST /api/portfolio/upload      │ Parse .xls/.xlsx, return a preview diff  │
 * │ POST /api/portfolio/confirm     │ Apply confirmed changes to the database   │
 * │ GET  /api/portfolio             │ View the user's current holdings          │
 * └─────────────────────────────────┴──────────────────────────────────────────┘
 *
 * Two-step flow (Upload → Confirm) ensures the user sees exactly what will
 * change before any data is written to the database.
 */
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioController.class);

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    // -----------------------------------------------------------------------
    // Step 1 — Upload & Preview
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Step 2 — Confirm & Persist
    // -----------------------------------------------------------------------

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

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    // -----------------------------------------------------------------------
    // View portfolio
    // -----------------------------------------------------------------------

    /**
     * Returns the authenticated user's current portfolio holdings sorted
     * alphabetically by symbol.
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
