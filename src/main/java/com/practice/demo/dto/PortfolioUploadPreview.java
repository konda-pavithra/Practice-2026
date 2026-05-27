package com.practice.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Returned by {@code POST /api/portfolio/upload}.
 *
 * The UI must display this to the user and ask for confirmation before
 * calling {@code POST /api/portfolio/confirm}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioUploadPreview {

    /** Stocks in the file that are NOT yet in the user's portfolio — will be added. */
    private List<PortfolioEntry> newStocks;

    /**
     * Stocks in the file that already exist in the user's portfolio — will be
     * updated (quantity and/or buying price will change).
     */
    private List<PortfolioUpdateItem> stocksToUpdate;

    /**
     * Raw names from the Excel sheet that could not be matched to any
     * Nifty 50 symbol. These rows will be ignored even after confirmation.
     */
    private List<String> invalidSymbols;

    /** Rows skipped due to data errors (non-numeric quantity/price, empty cells, etc.). */
    private List<String> parseErrors;

    /**
     * Human-readable summary message for the UI to display above the
     * confirmation dialog, e.g.:
     * "2 new stocks will be added. 1 existing stock will be updated.
     *  Please review the changes below and confirm."
     */
    private String userMessage;

    /**
     * true  → the file contains stocks that already exist in the user's
     *          portfolio; user must explicitly confirm the update.
     * false → only new stocks; can be applied without an update prompt.
     */
    private boolean requiresConfirmation;
}
