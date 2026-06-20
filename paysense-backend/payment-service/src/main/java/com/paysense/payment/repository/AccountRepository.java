package com.paysense.payment.repository;

import com.paysense.payment.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUserId(UUID userId);

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByUserId(UUID userId);

    boolean existsByAccountNumber(String accountNumber);

    /**
     * Pessimistic write lock for critical balance operations.
     * Use this when you need guaranteed exclusive access during a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(UUID id);
}
