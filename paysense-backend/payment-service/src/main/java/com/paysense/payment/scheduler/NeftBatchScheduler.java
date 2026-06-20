package com.paysense.payment.scheduler;

import com.paysense.payment.entity.Account;
import com.paysense.payment.entity.PaymentRequest;
import com.paysense.payment.event.PaymentEventPublisher;
import com.paysense.payment.repository.AccountRepository;
import com.paysense.payment.repository.PaymentRequestRepository;
import com.paysense.payment.simulator.NeftSimulator;
import com.paysense.payment.simulator.SimulatorResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * NEFT Batch Settlement Scheduler.
 *
 * Runs every 30 minutes to process all PENDING NEFT payment requests.
 * For each pending NEFT:
 *   1. Call NeftSimulator.settlePayment()
 *   2. If success: credit receiver account, update status to SUCCESS, set settled_at
 *   3. If failed: refund sender account, update status to FAILED
 *   4. Publish Kafka event
 */
@Component
@RequiredArgsConstructor
public class NeftBatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(NeftBatchScheduler.class);

    private final PaymentRequestRepository paymentRequestRepository;
    private final AccountRepository accountRepository;
    private final NeftSimulator neftSimulator;
    private final PaymentEventPublisher eventPublisher;

    /**
     * Runs every 30 minutes to settle pending NEFT payments.
     */
    @Scheduled(cron = "0 */30 * * * *")
    public void settleNeftPayments() {
        List<PaymentRequest> pendingNeft = paymentRequestRepository
                .findByPaymentTypeAndStatus("NEFT", "PENDING");

        if (pendingNeft.isEmpty()) {
            log.debug("NEFT batch: No pending payments to settle");
            return;
        }

        log.info("NEFT batch: Processing {} pending payments", pendingNeft.size());

        int settled = 0;
        int failed = 0;

        for (PaymentRequest pr : pendingNeft) {
            try {
                processNeftSettlement(pr);
                settled++;
            } catch (Exception e) {
                log.error("NEFT batch: Failed to process payment={}: {}", pr.getId(), e.getMessage(), e);
                failed++;
            }
        }

        log.info("NEFT batch complete: {} settled, {} failed out of {} total",
                settled, failed, pendingNeft.size());
    }

    @Transactional
    protected void processNeftSettlement(PaymentRequest pr) {
        SimulatorResult result = neftSimulator.settlePayment(pr.getUtrNumber(), pr.getAmount());

        if (result.isSuccess()) {
            // Credit receiver account
            Account receiverAccount = accountRepository.findByAccountNumber(pr.getReceiverAccountNo())
                    .orElse(null);

            if (receiverAccount != null) {
                receiverAccount.setBalance(receiverAccount.getBalance().add(pr.getAmount()));
                accountRepository.save(receiverAccount);
            }

            pr.setStatus("SUCCESS");
            pr.setSettledAt(LocalDateTime.now());
            paymentRequestRepository.save(pr);

            // Determine sender userId for event
            Account senderAccount = accountRepository.findById(pr.getSenderAccountId()).orElse(null);
            if (senderAccount != null) {
                eventPublisher.publishPaymentCompleted(pr, senderAccount.getUserId(),
                        receiverAccount != null ? receiverAccount.getId() : null);
            }

            log.info("NEFT settled: UTR={}, amount=₹{}, receiver={}",
                    pr.getUtrNumber(), pr.getAmount(), pr.getReceiverAccountNo());
        } else {
            // Settlement failed — refund sender
            Account senderAccount = accountRepository.findById(pr.getSenderAccountId())
                    .orElse(null);
            if (senderAccount != null) {
                senderAccount.setBalance(senderAccount.getBalance().add(pr.getAmount()));
                accountRepository.save(senderAccount);
                log.info("NEFT refund: ₹{} returned to account {}", pr.getAmount(), senderAccount.getAccountNumber());
            }

            pr.setStatus("FAILED");
            pr.setFailureReason(result.getFailureReason());
            pr.setSettledAt(LocalDateTime.now());
            paymentRequestRepository.save(pr);

            if (senderAccount != null) {
                eventPublisher.publishPaymentFailed(pr, senderAccount.getUserId());
            }

            log.warn("NEFT settlement failed: UTR={}, reason={}", pr.getUtrNumber(), result.getFailureReason());
        }
    }
}
