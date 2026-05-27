package com.practice.demo.repository;

import com.practice.demo.entity.StockThreshold;
import com.practice.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link StockThreshold} — one row per (user, symbol) pair.
 */
public interface StockThresholdRepository extends JpaRepository<StockThreshold, Long> {

    /** All thresholds for a user, sorted A→Z by symbol for consistent UI ordering. */
    List<StockThreshold> findByUserOrderBySymbolAsc(User user);

    /** Look up the threshold for a specific stock belonging to a user. */
    Optional<StockThreshold> findByUserAndSymbol(User user, String symbol);

    /** Fast existence check used before insert to decide create vs. update. */
    boolean existsByUserAndSymbol(User user, String symbol);

    /**
     * Loads every threshold that has a matching portfolio holding for the same
     * (user, symbol) pair, eagerly fetching the owning User in the same query.
     *
     * <p>Used by the alert generator scheduler so it only evaluates thresholds
     * for stocks the user actually owns (P&amp;L context required for the alert email).
     * The JOIN FETCH prevents LazyInitializationException when the scheduler reads
     * {@code threshold.getUser().getEmail()} outside of any transaction.
     */
    @Query("""
           SELECT t FROM StockThreshold t
           JOIN FETCH t.user
           WHERE EXISTS (
               SELECT p FROM Portfolio p
               WHERE p.user = t.user AND p.symbol = t.symbol
           )
           """)
    List<StockThreshold> findAllWithPortfolioHolding();
}
