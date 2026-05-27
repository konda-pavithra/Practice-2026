package com.practice.demo.repository;

import com.practice.demo.entity.StockThreshold;
import com.practice.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
