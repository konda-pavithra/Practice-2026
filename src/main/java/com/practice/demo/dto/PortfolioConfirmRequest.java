package com.practice.demo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent by the client to {@code POST /api/portfolio/confirm} after the user
 * has reviewed the upload preview and clicked "Confirm".
 *
 * The client echoes back the {@code newStocks} and (optionally) the
 * {@code stocksToUpdate} from the preview — only entries that the user
 * approved should be included.
 */
@Data
public class PortfolioConfirmRequest {

    /** New stocks to add (not previously in the portfolio). */
    private List<PortfolioEntry> toAdd = new ArrayList<>();

    /**
     * Existing stocks whose quantity/buying-price should be overwritten.
     * Only included if the user accepted the update prompt.
     */
    private List<PortfolioEntry> toUpdate = new ArrayList<>();
}
