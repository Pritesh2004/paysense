package com.paysense.payment.simulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Simulates NEFT batch payment processing.
 *
 * Unlike UPI, NEFT returns immediately (no latency simulation at initiation).
 * The actual settlement is done by NeftBatchScheduler which calls settlePayment().
 *
 * - Settlement success rate: 95%
 * - UTR format: PAYS{YYYYMMDD}{6-digit-random}
 */
@Component
public class NeftSimulator {

    private static final Logger log = LoggerFactory.getLogger(NeftSimulator.class);
    private static final double SUCCESS_RATE = 0.95;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final Random random = new Random();

    /**
     * Immediately creates a NEFT payment initiation (no latency).
     * Returns a pending result — actual settlement happens in batch.
     *
     * @param amount the payment amount
     * @return SimulatorResult indicating that the NEFT was accepted for processing
     */
    public SimulatorResult initiate(BigDecimal amount) {
        String utr = generateUtr();
        log.info("NEFT simulator: INITIATED — UTR={}, amount=₹{} (pending batch settlement)", utr, amount);
        return SimulatorResult.ok(utr, 0);
    }

    /**
     * Settles a previously initiated NEFT payment during batch processing.
     * Called by NeftBatchScheduler every 30 minutes.
     *
     * @param utrNumber the UTR from initiation
     * @param amount    the payment amount
     * @return SimulatorResult with settlement outcome
     */
    public SimulatorResult settlePayment(String utrNumber, BigDecimal amount) {
        boolean success = random.nextDouble() < SUCCESS_RATE;

        if (success) {
            log.info("NEFT simulator: SETTLED — UTR={}, amount=₹{}", utrNumber, amount);
            return SimulatorResult.ok(utrNumber, 0);
        } else {
            String reason = pickFailureReason();
            log.warn("NEFT simulator: SETTLEMENT FAILED — UTR={}, reason={}, amount=₹{}", utrNumber, reason, amount);
            return SimulatorResult.fail(reason, 0);
        }
    }

    private String generateUtr() {
        String datePart = LocalDate.now().format(DATE_FMT);
        int randomPart = 100_000 + random.nextInt(900_000);
        return "PAYS" + datePart + randomPart;
    }

    private String pickFailureReason() {
        String[] reasons = {
                "NEFT_SETTLEMENT_TIMEOUT",
                "RECEIVER_ACCOUNT_INVALID",
                "NEFT_BATCH_PROCESSING_ERROR",
                "BANK_SYSTEM_UNAVAILABLE"
        };
        return reasons[random.nextInt(reasons.length)];
    }
}
