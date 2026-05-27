package com.practice.demo.repository;

import com.practice.demo.entity.Portfolio;
import com.practice.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    /** All holdings for a user, sorted alphabetically by symbol. */
    List<Portfolio> findByUserOrderBySymbolAsc(User user);

    /** Lookup a specific stock within a user's portfolio. */
    Optional<Portfolio> findByUserAndSymbol(User user, String symbol);

    /** Quick existence check — avoids fetching the full entity. */
    boolean existsByUserAndSymbol(User user, String symbol);
}
