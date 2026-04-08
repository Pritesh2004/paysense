package com.paysense.payment.repository;

import com.paysense.payment.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    /**
     * Find wallets whose daily spend hasn't been reset today — used by midnight scheduler.
     */
    List<Wallet> findByLastResetDateBefore(LocalDate date);

    /**
     * Bulk reset all wallet daily spends — midnight batch.
     */
    @Modifying
    @Query("UPDATE Wallet w SET w.todaySpent = 0, w.lastResetDate = CURRENT_DATE WHERE w.lastResetDate < CURRENT_DATE")
    int resetDailySpends();
}
