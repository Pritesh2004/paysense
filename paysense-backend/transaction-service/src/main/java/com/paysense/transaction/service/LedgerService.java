package com.paysense.transaction.service;

import com.paysense.transaction.dto.CreateLedgerRequest;
import com.paysense.transaction.dto.LedgerEntryResponse;
import com.paysense.transaction.entity.LedgerEntry;
import com.paysense.transaction.exception.InvalidTransactionException;
import com.paysense.transaction.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Manages immutable ledger entries using double-entry bookkeeping.
 * Each payment creates exactly 2 rows: DEBIT for sender, CREDIT for receiver.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);
    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * Creates a double-entry ledger pair: DEBIT for sender, CREDIT for receiver.
     * balance_after is provided by the caller (Payment Service knows the post-transfer balance).
     */
    @Transactional
    public List<LedgerEntryResponse> createDoubleEntry(CreateLedgerRequest request) {
        validateRequest(request);

        // Compute balance_after if not provided
        BigDecimal senderBalanceAfter = request.getSenderBalanceAfter();
        BigDecimal receiverBalanceAfter = request.getReceiverBalanceAfter();

        if (senderBalanceAfter == null) {
            // Fallback: compute from last ledger entry for sender
            senderBalanceAfter = getLatestBalance(request.getSenderAccountId())
                    .subtract(request.getAmount());
        }
        if (receiverBalanceAfter == null) {
            // Fallback: compute from last ledger entry for receiver
            receiverBalanceAfter = getLatestBalance(request.getReceiverAccountId())
                    .add(request.getAmount());
        }

        // DEBIT entry for sender
        LedgerEntry debitEntry = LedgerEntry.builder()
                .paymentRequestId(request.getPaymentRequestId())
                .accountId(request.getSenderAccountId())
                .entryType("DEBIT")
                .amount(request.getAmount())
                .balanceAfter(senderBalanceAfter)
                .description(request.getDescription())
                .paymentType(request.getType())
                .category(request.getCategory())
                .build();

        // CREDIT entry for receiver
        LedgerEntry creditEntry = LedgerEntry.builder()
                .paymentRequestId(request.getPaymentRequestId())
                .accountId(request.getReceiverAccountId())
                .entryType("CREDIT")
                .amount(request.getAmount())
                .balanceAfter(receiverBalanceAfter)
                .description(request.getDescription())
                .paymentType(request.getType())
                .category(request.getCategory())
                .build();

        LedgerEntry savedDebit = ledgerEntryRepository.save(debitEntry);
        LedgerEntry savedCredit = ledgerEntryRepository.save(creditEntry);

        log.info("Double-entry created: paymentRequestId={}, DEBIT={}, CREDIT={}, amount={}",
                request.getPaymentRequestId(), savedDebit.getId(), savedCredit.getId(), request.getAmount());

        return List.of(mapToResponse(savedDebit), mapToResponse(savedCredit));
    }

    private BigDecimal getLatestBalance(java.util.UUID accountId) {
        return ledgerEntryRepository.findTopByAccountIdOrderByCreatedAtDesc(accountId)
                .map(LedgerEntry::getBalanceAfter)
                .orElse(BigDecimal.ZERO);
    }

    private void validateRequest(CreateLedgerRequest request) {
        if (request.getSenderAccountId().equals(request.getReceiverAccountId())) {
            throw new InvalidTransactionException("Sender and receiver accounts cannot be the same");
        }
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Amount must be positive");
        }
    }

    private LedgerEntryResponse mapToResponse(LedgerEntry entry) {
        return LedgerEntryResponse.builder()
                .id(entry.getId())
                .paymentRequestId(entry.getPaymentRequestId())
                .accountId(entry.getAccountId())
                .entryType(entry.getEntryType())
                .amount(entry.getAmount())
                .balanceAfter(entry.getBalanceAfter())
                .description(entry.getDescription())
                .paymentType(entry.getPaymentType())
                .category(entry.getCategory())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
