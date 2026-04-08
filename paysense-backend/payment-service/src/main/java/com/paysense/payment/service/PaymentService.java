package com.paysense.payment.service;

import com.paysense.payment.dto.NeftPaymentRequestDto;
import com.paysense.payment.dto.PaymentResponse;
import com.paysense.payment.dto.UpiPaymentRequestDto;
import com.paysense.payment.entity.Account;
import com.paysense.payment.entity.PaymentRequest;
import com.paysense.payment.entity.VpaRegistry;
import com.paysense.payment.event.PaymentEventPublisher;
import com.paysense.payment.exception.AccountNotFoundException;
import com.paysense.payment.exception.InsufficientFundsException;
import com.paysense.payment.exception.PaymentException;
import com.paysense.payment.repository.AccountRepository;
import com.paysense.payment.repository.PaymentRequestRepository;
import com.paysense.payment.repository.VpaRegistryRepository;
import com.paysense.payment.simulator.NeftSimulator;
import com.paysense.payment.simulator.SimulatorResult;
import com.paysense.payment.simulator.UpiSimulator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core payment processing service.
 *
 * Handles UPI and NEFT payment flows with:
 * - Idempotency key check (prevents duplicate payments)
 * - Fraud service integration (circuit breaker protected)
 * - UPI/NEFT simulation
 * - Atomic balance transfers with optimistic locking
 * - Kafka event publishing
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final AccountRepository accountRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final VpaRegistryRepository vpaRegistryRepository;
    private final UpiSimulator upiSimulator;
    private final NeftSimulator neftSimulator;
    private final FraudServiceClient fraudServiceClient;
    private final PaymentEventPublisher eventPublisher;

    // ── UPI Payment ─────────────────────────────────────────

    /**
     * Process a UPI payment.
     *
     * Flow:
     * 1. Check idempotency key — if exists, return cached PaymentResponse
     * 2. Find sender account by userId, check status ACTIVE
     * 3. Resolve receiver VPA to account via vpa_registry
     * 4. Check sender balance >= amount
     * 5. Call fraud service (circuit breaker protected)
     * 6. Call UpiSimulator.process()
     * 7. If success: debit sender + credit receiver + create payment_request
     * 8. Publish PaymentCompletedEvent to Kafka
     * 9. Return PaymentResponse with UTR
     */
    public PaymentResponse processUpiPayment(UpiPaymentRequestDto request, String idempotencyKey, UUID userId) {
        // 1. Idempotency check
        Optional<PaymentRequest> existing = paymentRequestRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent UPI payment detected: key={}", idempotencyKey);
            return mapToResponse(existing.get(), "Payment already processed (idempotent)");
        }

        // 2. Find and validate sender account
        Account senderAccount = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException("Sender account not found for user: " + userId));
        if (!"ACTIVE".equals(senderAccount.getStatus())) {
            throw new PaymentException("Sender account is not active. Status: " + senderAccount.getStatus());
        }

        // 3. Resolve receiver VPA
        VpaRegistry receiverVpaEntry = vpaRegistryRepository.findByVpaAndIsActiveTrue(request.getReceiverVpa())
                .orElseThrow(() -> new PaymentException("Receiver VPA not found or inactive: " + request.getReceiverVpa()));
        Account receiverAccount = receiverVpaEntry.getAccount();

        // Self-payment check
        if (senderAccount.getId().equals(receiverAccount.getId())) {
            throw new PaymentException("Cannot send UPI payment to yourself");
        }

        // 4. Check sender balance
        if (senderAccount.getBalance().compareTo(request.getAmount()) < 0) {
            // Create a FAILED payment request for audit
            PaymentRequest failedPr = createPaymentRequest(idempotencyKey, senderAccount.getId(),
                    request.getReceiverVpa(), null, null, request.getAmount(),
                    "UPI", "FAILED", "INSUFFICIENT_FUNDS", null, request.getDescription());
            eventPublisher.publishPaymentFailed(failedPr, userId);
            throw new InsufficientFundsException(
                    "Insufficient balance. Available: ₹" + senderAccount.getBalance() + ", Requested: ₹" + request.getAmount());
        }

        // 5. Fraud check (circuit breaker protected)
        FraudServiceClient.FraudCheckResult fraudResult = fraudServiceClient.checkFraud(
                userId, UUID.randomUUID(), request.getAmount(), "UPI", request.getReceiverVpa());
        if (fraudResult.isBlocked()) {
            PaymentRequest blockedPr = createPaymentRequest(idempotencyKey, senderAccount.getId(),
                    request.getReceiverVpa(), null, null, request.getAmount(),
                    "UPI", "FAILED", "BLOCKED_BY_FRAUD_CHECK", null, request.getDescription());
            eventPublisher.publishPaymentFailed(blockedPr, userId);
            throw new PaymentException("Payment blocked by fraud detection. Risk score: " + fraudResult.getRiskScore());
        }

        // 6. Call UPI simulator
        SimulatorResult simResult = upiSimulator.process(request.getAmount());

        if (simResult.isSuccess()) {
            // 7. Atomic balance transfer
            return executeUpiTransfer(senderAccount, receiverAccount, request, idempotencyKey,
                    simResult.getUtrNumber(), userId);
        } else {
            // Simulator failed — create FAILED payment request
            PaymentRequest failedPr = createPaymentRequest(idempotencyKey, senderAccount.getId(),
                    request.getReceiverVpa(), null, null, request.getAmount(),
                    "UPI", "FAILED", simResult.getFailureReason(), null, request.getDescription());
            eventPublisher.publishPaymentFailed(failedPr, userId);
            return mapToResponse(failedPr, "UPI payment failed: " + simResult.getFailureReason());
        }
    }

    @Transactional
    protected PaymentResponse executeUpiTransfer(Account sender, Account receiver,
                                                  UpiPaymentRequestDto request, String idempotencyKey,
                                                  String utrNumber, UUID userId) {
        // Re-read with latest version for optimistic locking
        sender = accountRepository.findById(sender.getId())
                .orElseThrow(() -> new AccountNotFoundException("Sender account disappeared"));
        receiver = accountRepository.findById(receiver.getId())
                .orElseThrow(() -> new AccountNotFoundException("Receiver account disappeared"));

        // Double-check balance (could have changed between validation and lock)
        if (sender.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException("Insufficient balance after re-read (concurrent modification)");
        }

        // Debit sender
        sender.setBalance(sender.getBalance().subtract(request.getAmount()));
        accountRepository.save(sender);

        // Credit receiver
        receiver.setBalance(receiver.getBalance().add(request.getAmount()));
        accountRepository.save(receiver);

        // Create SUCCESS payment request
        PaymentRequest pr = createPaymentRequest(idempotencyKey, sender.getId(),
                request.getReceiverVpa(), null, null, request.getAmount(),
                "UPI", "SUCCESS", null, utrNumber, request.getDescription());
        pr.setSettledAt(LocalDateTime.now());
        paymentRequestRepository.save(pr);

        // 8. Publish Kafka event
        eventPublisher.publishPaymentCompleted(pr, userId, receiver.getId());

        log.info("UPI payment SUCCESS: ₹{} from {} to {}, UTR={}",
                request.getAmount(), sender.getAccountNumber(), request.getReceiverVpa(), utrNumber);

        return mapToResponse(pr, "UPI payment successful. UTR: " + utrNumber);
    }

    // ── NEFT Payment ────────────────────────────────────────

    /**
     * Initiate a NEFT payment.
     *
     * NEFT deducts the balance immediately (held) but creates the payment_request
     * with status PENDING. Actual settlement happens in NeftBatchScheduler.
     */
    public PaymentResponse initiateNeftPayment(NeftPaymentRequestDto request, String idempotencyKey, UUID userId) {
        // 1. Idempotency check
        Optional<PaymentRequest> existing = paymentRequestRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent NEFT payment detected: key={}", idempotencyKey);
            return mapToResponse(existing.get(), "NEFT payment already initiated (idempotent)");
        }

        // 2. Find and validate sender account
        Account senderAccount = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException("Sender account not found for user: " + userId));
        if (!"ACTIVE".equals(senderAccount.getStatus())) {
            throw new PaymentException("Sender account is not active");
        }

        // 3. Validate receiver account exists (internal NEFT)
        Account receiverAccount = accountRepository.findByAccountNumber(request.getReceiverAccountNo())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Receiver account not found: " + request.getReceiverAccountNo()));

        // Self-payment check
        if (senderAccount.getId().equals(receiverAccount.getId())) {
            throw new PaymentException("Cannot send NEFT payment to yourself");
        }

        // 4. Check sender balance
        if (senderAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient balance for NEFT. Available: ₹" + senderAccount.getBalance());
        }

        // 5. Fraud check
        FraudServiceClient.FraudCheckResult fraudResult = fraudServiceClient.checkFraud(
                userId, UUID.randomUUID(), request.getAmount(), "NEFT", null);
        if (fraudResult.isBlocked()) {
            throw new PaymentException("NEFT payment blocked by fraud detection");
        }

        // 6. Initiate NEFT (returns immediately)
        return executeNeftInitiation(senderAccount, request, idempotencyKey, userId);
    }

    @Transactional
    protected PaymentResponse executeNeftInitiation(Account sender, NeftPaymentRequestDto request,
                                                     String idempotencyKey, UUID userId) {
        // Re-read for optimistic locking
        sender = accountRepository.findById(sender.getId())
                .orElseThrow(() -> new AccountNotFoundException("Sender account disappeared"));

        if (sender.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException("Insufficient balance after re-read");
        }

        // Debit sender immediately (hold)
        sender.setBalance(sender.getBalance().subtract(request.getAmount()));
        accountRepository.save(sender);

        // Generate UTR via NEFT simulator
        SimulatorResult simResult = neftSimulator.initiate(request.getAmount());

        // Create PENDING payment request
        PaymentRequest pr = createPaymentRequest(idempotencyKey, sender.getId(),
                null, request.getReceiverAccountNo(), request.getReceiverIfsc(),
                request.getAmount(), "NEFT", "PENDING",
                null, simResult.getUtrNumber(), request.getDescription());

        // Publish initiation event
        eventPublisher.publishPaymentInitiated(pr, userId);

        log.info("NEFT payment INITIATED: ₹{} from {} to {}, UTR={}, status=PENDING",
                request.getAmount(), sender.getAccountNumber(), request.getReceiverAccountNo(),
                simResult.getUtrNumber());

        return mapToResponse(pr, "NEFT initiated successfully. Settlement within 30 minutes. UTR: " + simResult.getUtrNumber());
    }

    // ── Payment History ─────────────────────────────────────

    /**
     * Get all payments for a user's account.
     */
    public List<PaymentResponse> getPaymentHistory(UUID userId) {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found for user: " + userId));

        return paymentRequestRepository.findBySenderAccountId(account.getId())
                .stream()
                .map(pr -> mapToResponse(pr, null))
                .toList();
    }

    /**
     * Get a specific payment by ID.
     */
    public PaymentResponse getPaymentById(UUID paymentId) {
        PaymentRequest pr = paymentRequestRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found: " + paymentId));
        return mapToResponse(pr, null);
    }

    // ── Helpers ─────────────────────────────────────────────

    private PaymentRequest createPaymentRequest(String idempotencyKey, UUID senderAccountId,
                                                 String receiverVpa, String receiverAccountNo,
                                                 String receiverIfsc, BigDecimal amount,
                                                 String paymentType, String status,
                                                 String failureReason, String utrNumber,
                                                 String description) {
        PaymentRequest pr = PaymentRequest.builder()
                .idempotencyKey(idempotencyKey)
                .senderAccountId(senderAccountId)
                .receiverVpa(receiverVpa)
                .receiverAccountNo(receiverAccountNo)
                .receiverIfsc(receiverIfsc)
                .amount(amount)
                .paymentType(paymentType)
                .status(status)
                .failureReason(failureReason)
                .utrNumber(utrNumber)
                .description(description)
                .build();
        return paymentRequestRepository.save(pr);
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
}
