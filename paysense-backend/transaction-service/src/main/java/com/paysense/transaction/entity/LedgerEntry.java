package com.paysense.transaction.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable ledger entry — double-entry bookkeeping.
 * Each successful payment creates exactly 2 rows: DEBIT for sender, CREDIT for receiver.
 * This table is NEVER updated — only inserted. Financial audit requirement.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "payment_request_id", nullable = false)
    private UUID paymentRequestId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "entry_type", nullable = false, length = 6)
    private String entryType; // DEBIT or CREDIT

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "payment_type", length = 10)
    private String paymentType; // UPI, NEFT, WALLET, TOPUP

    @Column(length = 50)
    private String category; // FOOD, TRANSPORT, SHOPPING, etc.

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
