package com.practice.demo.controller;

import com.practice.demo.dto.ThresholdRequest;
import com.practice.demo.dto.ThresholdResponse;
import com.practice.demo.service.ThresholdService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Stock price threshold management — all endpoints require a valid JWT.
 *
 * ┌──────────────────────────────────────┬──────────────────────────────────────────────────┐
 * │ Endpoint                             │ Purpose                                          │
 * ├──────────────────────────────────────┼──────────────────────────────────────────────────┤
 * │ PUT    /api/thresholds/{symbol}      │ Set or update thresholds for a stock (upsert)    │
 * │ GET    /api/thresholds               │ List all thresholds configured by the user        │
 * │ GET    /api/thresholds/{symbol}      │ Get thresholds for one specific stock             │
 * │ DELETE /api/thresholds/{symbol}      │ Remove thresholds for a stock                    │
 * └──────────────────────────────────────┴──────────────────────────────────────────────────┘
 *
 * Threshold semantics:
 *   upperThresholdPercent — positive %; alert fires when price ≥ referencePrice × (1 + upper/100)
 *   lowerThresholdPercent — positive %; alert fires when price ≤ referencePrice × (1 − lower/100)
 *   referencePrice        — market price snapshotted at the moment of last save
 *   upperAlertPrice       — pre-computed absolute upper trigger level (₹)
 *   lowerAlertPrice       — pre-computed absolute lower trigger level (₹)
 *
 * Symbol resolution: accepts bare ticker ("RELIANCE"), qualified form ("RELIANCE.NS"),
 * or company display name ("Reliance Industries Limited") in the path variable.
 */
@Tag(
    name        = "Price Thresholds",
    description = "Set upper/lower % alert thresholds per stock. When the live price crosses a "
                + "threshold the system sends an email via RabbitMQ. "
                + "All endpoints require a valid JWT."
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/thresholds")
public class ThresholdController {

    private static final Logger logger = LoggerFactory.getLogger(ThresholdController.class);

    private final ThresholdService thresholdService;

    public ThresholdController(ThresholdService thresholdService) {
        this.thresholdService = thresholdService;
    }

    // =========================================================================
    // Set / change threshold (upsert)
    // =========================================================================

    @Operation(
        summary     = "Set or update thresholds for a stock",
        description = "**Upsert** — creates the threshold record on the first call; "
                    + "subsequent calls update the percentages and refresh the reference price "
                    + "from the current market cache. "
                    + "The response includes pre-computed absolute alert prices in ₹."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description  = "Threshold saved; response includes referencePrice and alert trigger prices",
            content      = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema    = @Schema(implementation = ThresholdResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description  = "Invalid symbol or non-positive percentage value",
            content      = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    @PutMapping("/{symbol}")
    public ResponseEntity<ThresholdResponse> setThreshold(
            @Parameter(description = "Stock ticker or company name", example = "RELIANCE")
            @PathVariable String symbol,
            @RequestBody ThresholdRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("PUT /api/thresholds/{} — user '{}': upper={}%, lower={}%",
                symbol, username,
                request.getUpperThresholdPercent(), request.getLowerThresholdPercent());

        ThresholdResponse response = thresholdService.setThreshold(symbol, request, username);

        logger.info("PUT /api/thresholds/{} — user '{}': saved — refPrice={}, upperAlert=₹{}, lowerAlert=₹{}",
                symbol, username,
                response.getReferencePrice(),
                response.getUpperAlertPrice(),
                response.getLowerAlertPrice());

        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Read all thresholds
    // =========================================================================

    @Operation(
        summary     = "List all threshold configurations",
        description = "Returns every threshold the authenticated user has configured, "
                    + "sorted alphabetically by symbol. Returns an empty list if none exist."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description  = "Threshold list returned (may be empty)",
            content      = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                array     = @ArraySchema(schema = @Schema(implementation = ThresholdResponse.class))
            )
        ),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    @GetMapping
    public ResponseEntity<List<ThresholdResponse>> getAllThresholds(Authentication authentication) {
        String username = authentication.getName();
        logger.info("GET /api/thresholds — user '{}'", username);

        List<ThresholdResponse> list = thresholdService.getAllThresholds(username);

        logger.info("GET /api/thresholds — user '{}': {} threshold(s) returned", username, list.size());
        return ResponseEntity.ok(list);
    }

    // =========================================================================
    // Read single threshold
    // =========================================================================

    @Operation(
        summary     = "Get threshold for a single stock",
        description = "Returns the threshold configuration (percentages + computed alert prices) "
                    + "for the specified stock."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description  = "Threshold returned",
            content      = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema    = @Schema(implementation = ThresholdResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description  = "Invalid symbol, or no threshold set for that stock",
            content      = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    @GetMapping("/{symbol}")
    public ResponseEntity<ThresholdResponse> getThreshold(
            @Parameter(description = "Stock ticker or company name", example = "INFY")
            @PathVariable String symbol,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("GET /api/thresholds/{} — user '{}'", symbol, username);

        ThresholdResponse response = thresholdService.getThreshold(symbol, username);

        logger.info("GET /api/thresholds/{} — user '{}': upper={}%, lower={}%, refPrice={}",
                symbol, username,
                response.getUpperThresholdPercent(),
                response.getLowerThresholdPercent(),
                response.getReferencePrice());

        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Delete threshold
    // =========================================================================

    @Operation(
        summary     = "Delete a stock's threshold configuration",
        description = "Removes the threshold record for the specified stock. "
                    + "No further alerts will be sent for that stock until a new threshold is set."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Threshold deleted"),
        @ApiResponse(
            responseCode = "400",
            description  = "Invalid symbol or no threshold set for that stock",
            content      = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> deleteThreshold(
            @Parameter(description = "Stock ticker or company name", example = "TCS")
            @PathVariable String symbol,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("DELETE /api/thresholds/{} — user '{}'", symbol, username);

        thresholdService.deleteThreshold(symbol, username);

        logger.info("DELETE /api/thresholds/{} — user '{}': threshold removed", symbol, username);
        return ResponseEntity.noContent().build();
    }
}
