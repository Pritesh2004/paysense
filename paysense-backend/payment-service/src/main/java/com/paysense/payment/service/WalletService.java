package com.paysense.payment.service;

import com.paysense.payment.dto.PaymentResponse;
import com.paysense.payment.entity.Account;
import com.paysense.payment.entity.Wallet;
import com.paysense.payment.entity.PaymentRequest;
import com.paysense.payment.entity.VpaRegistry;
import com.paysense.payment.exception.AccountNotFoundException;
import com.paysense.payment.exception.InsufficientFundsException;
import com.paysense.payment.exception.PaymentException;
import com.paysense.payment.repository.AccountRepository;
import com.paysense.payment.repository.PaymentRequestRepository;
import com.paysense.payment.repository.VpaRegistryRepository;
import com.paysense.payment.repository.WalletRepository;
import com.paysense.payment.event.PaymentEventPublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Wallet operations: top-up (account → wallet) and pay (wallet → receiver wallet).
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;
    private final AccountRepository accountRepository;
    private final VpaRegistryRepository vpaRegistryRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentEventPublisher eventPublisher;

    /**
     * Top-up wallet from the user's account balance.
     */
    @Transactional
    public PaymentResponse topupWallet(UUID userId, BigDecimal amount, String idempotencyKey) {
        // Idempotency check
        var existing = paymentRequestRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent wallet top-up detected: key={}", idempotencyKey);
            return mapToResponse(existing.get(), "Top-up already processed (idempotent)");
        }

        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found for user: " + userId));
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new PaymentException("Wallet not found for user: " + userId));

        // Check account balance
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient account balance. Available: ₹" + account.getBalance() + ", Requested: ₹" + amount);
        }

        // Debit account, credit wallet
        account.setBalance(account.getBalance().subtract(amount));
        wallet.setBalance(wallet.getBalance().add(amount));

        accountRepository.save(account);
        walletRepository.save(wallet);

        // Create payment request record
        String utr = generateWalletUtr("WTOP");
        PaymentRequest pr = PaymentRequest.builder()
                .idempotencyKey(idempotencyKey)
                .senderAccountId(account.getId())
                .amount(amount)
                .paymentType("TOPUP")
                .status("SUCCESS")
                .utrNumber(utr)
                .description("Wallet top-up from account")
                .settledAt(LocalDateTime.now())
                .build();
        paymentRequestRepository.save(pr);

        log.info("Wallet top-up: ₹{} from account {} to wallet, user={}", amount, account.getAccountNumber(), userId);
        return mapToResponse(pr, "Wallet top-up successful. New wallet balance: ₹" + wallet.getBalance());
    }

    /**
     * Pay from wallet to another user's wallet (via VPA).
     * Subject to daily spending limit.
     */
    @Transactional
    public PaymentResponse walletPay(UUID userId, String receiverVpa, BigDecimal amount,
                                     String description, String idempotencyKey) {
        // Idempotency check
        var existing = paymentRequestRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent wallet payment detected: key={}", idempotencyKey);
            return mapToResponse(existing.get(), "Payment already processed (idempotent)");
        }

        Wallet senderWallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new PaymentException("Wallet not found for user: " + userId));
        Account senderAccount = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found for user: " + userId));

        // Reset daily spend if needed
        resetDailySpendIfNeeded(senderWallet);

        // Check wallet balance
        if (senderWallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient wallet balance. Available: ₹" + senderWallet.getBalance());
        }

        // Check daily limit
        BigDecimal newTotalSpent = senderWallet.getTodaySpent().add(amount);
        if (newTotalSpent.compareTo(senderWallet.getDailyLimit()) > 0) {
            BigDecimal remaining = senderWallet.getDailyLimit().subtract(senderWallet.getTodaySpent());
            throw new PaymentException(
                    "Daily wallet limit exceeded. Remaining today: ₹" + remaining.max(BigDecimal.ZERO));
        }

        // Resolve receiver VPA → receiver's wallet
        VpaRegistry receiverVpaEntry = vpaRegistryRepository.findByVpaAndIsActiveTrue(receiverVpa)
                .orElseThrow(() -> new PaymentException("Receiver VPA not found: " + receiverVpa));
        Account receiverAccount = receiverVpaEntry.getAccount();
        Wallet receiverWallet = walletRepository.findByUserId(receiverAccount.getUserId())
                .orElseThrow(() -> new PaymentException("Receiver wallet not found"));

        // Self-payment check
        if (userId.equals(receiverAccount.getUserId())) {
            throw new PaymentException("Cannot send wallet payment to yourself");
        }

        // Debit sender wallet, credit receiver wallet
        senderWallet.setBalance(senderWallet.getBalance().subtract(amount));
        senderWallet.setTodaySpent(newTotalSpent);
        receiverWallet.setBalance(receiverWallet.getBalance().add(amount));

        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        // Create payment request
        String utr = generateWalletUtr("WPAY");
        PaymentRequest pr = PaymentRequest.builder()
                .idempotencyKey(idempotencyKey)
                .senderAccountId(senderAccount.getId())
                .receiverVpa(receiverVpa)
                .amount(amount)
                .paymentType("WALLET")
                .status("SUCCESS")
                .utrNumber(utr)
                .description(description != null ? description : "Wallet payment to " + receiverVpa)
                .settledAt(LocalDateTime.now())
                .build();
        paymentRequestRepository.save(pr);

        // Publish Kafka event
        eventPublisher.publishPaymentCompleted(pr, userId, receiverAccount.getId());

        log.info("Wallet payment: ₹{} from user={} to VPA={}", amount, userId, receiverVpa);
        return mapToResponse(pr, "Wallet payment successful");
    }

    private void resetDailySpendIfNeeded(Wallet wallet) {
        if (wallet.getLastResetDate() == null || wallet.getLastResetDate().isBefore(LocalDate.now())) {
            wallet.setTodaySpent(BigDecimal.ZERO);
            wallet.setLastResetDate(LocalDate.now());
        }
    }

    private PaymentResponse mapToResponse(PaymentRequest pr, String message) {
        return PaymentResponse.builder()
                .paymentRequestId(pr.getId())
                .idempotencyKey(pr.getIdempotencyKey())
                .utrNumber(pr.getUtrNumber())
                .amount(pr.getAmount())
                .paymentType(pr.getPaymentType())
                .status(pr.getStatus())
                .failureReason(pr.getFailureReason())
                .description(pr.getDescription())
                .initiatedAt(pr.getInitiatedAt())
                .settledAt(pr.getSettledAt())
                .message(message)
                .build();
    }

    private String generateWalletUtr(String prefix) {
        String datePart = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        int randomPart = 100_000 + new java.util.Random().nextInt(900_000); 
        return prefix + datePart + randomPart;
    }
}
