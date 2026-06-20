package com.paysense.transaction.repository;

import com.paysense.transaction.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * Find all ledger entries for an account, ordered by creation time (most recent first).
     */
    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    /**
     * Find ledger entries for an account within a date range.
     */
    List<LedgerEntry> findByAccountIdAndCreatedAtBetween(UUID accountId,
                                                          LocalDateTime start,
                                                          LocalDateTime end);

    /**
     * Get the most recent ledger entry for an account (to determine current balance).
     */
    Optional<LedgerEntry> findTopByAccountIdOrderByCreatedAtDesc(UUID accountId);

    /**
     * Spending summary: total DEBIT amount per category for a given account in a month.
     */
    @Query("SELECT l.category, SUM(l.amount) FROM LedgerEntry l " +
           "WHERE l.accountId = :accountId AND l.entryType = 'DEBIT' " +
           "AND l.createdAt >= :start AND l.createdAt < :end " +
           "GROUP BY l.category")
    List<Object[]> getSpendingByCategory(@Param("accountId") UUID accountId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    /**
     * Total spending (DEBIT) for an account in a date range.
     */
    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l " +
           "WHERE l.accountId = :accountId AND l.entryType = 'DEBIT' " +
           "AND l.createdAt >= :start AND l.createdAt < :end")
    BigDecimal getTotalSpending(@Param("accountId") UUID accountId,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    /**
     * Recent transactions for an account with optional category filter.
     */
    @Query("SELECT l FROM LedgerEntry l WHERE l.accountId = :accountId " +
           "AND (:category IS NULL OR l.category = :category) " +
           "ORDER BY l.createdAt DESC")
    Page<LedgerEntry> findRecentByAccountAndCategory(@Param("accountId") UUID accountId,
                                                      @Param("category") String category,
                                                      Pageable pageable);

    /**
     * Get all DEBIT entries for an account in a date range grouped for unusual spending detection.
     */
    @Query("SELECT l.category, SUM(l.amount), COUNT(l) FROM LedgerEntry l " +
           "WHERE l.accountId = :accountId AND l.entryType = 'DEBIT' " +
           "AND l.createdAt >= :start AND l.createdAt < :end " +
           "GROUP BY l.category")
    List<Object[]> getSpendingByCategoryInRange(@Param("accountId") UUID accountId,
                                                 @Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);

    /**
     * Find individual DEBIT transactions for a category in a date range.
     */
    @Query("SELECT l FROM LedgerEntry l WHERE l.accountId = :accountId " +
           "AND l.entryType = 'DEBIT' AND l.category = :category " +
           "AND l.createdAt >= :start AND l.createdAt < :end " +
           "ORDER BY l.amount DESC")
    List<LedgerEntry> findDebitsByCategoryInRange(@Param("accountId") UUID accountId,
                                                   @Param("category") String category,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);
}
