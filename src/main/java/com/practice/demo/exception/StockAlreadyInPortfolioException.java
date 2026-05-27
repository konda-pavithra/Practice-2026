package com.practice.demo.exception;

import com.practice.demo.dto.PortfolioResponse;

/**
 * Thrown when a user tries to add a stock that already exists in their portfolio.
 * Carries the existing holding so the UI can pre-fill the update form.
 */
public class StockAlreadyInPortfolioException extends RuntimeException {

    private final PortfolioResponse existingHolding;

    public StockAlreadyInPortfolioException(String message, PortfolioResponse existingHolding) {
        super(message);
        this.existingHolding = existingHolding;
    }

    public PortfolioResponse getExistingHolding() {
        return existingHolding;
    }
}
