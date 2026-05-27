package com.practice.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Returned by {@code POST /api/portfolio/confirm}.
 * Summarises the changes that were persisted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioConfirmResponse {

    private int addedCount;
    private int updatedCount;
    private int skippedCount;   // entries that failed re-validation or were duplicates
    private String message;

    /** The user's full updated portfolio, ready to render in the UI. */
    private List<PortfolioResponse> portfolio;
}
