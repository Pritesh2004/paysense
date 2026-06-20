package com.paysense.payment.simulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Simulates UPI payment gateway processing.
 *
 * - Latency: 50–200ms (simulated network delay)
 * - Success rate: 95%
 * - UTR format: PAYS{YYYYMMDD}{6-digit-random}
 */
@Component
public class UpiSimulator {

    private static final Logger log = LoggerFactory.getLogger(UpiSimulator.class);
    private static final double SUCCESS_RATE = 0.95;
    private static final int MIN_LATENCY_MS = 50;
    private static final int MAX_LATENCY_MS = 200;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final Random random = new Random();

    /**
     * Simulate a UPI payment processing.
     *
     * @param amount the payment amount (for logging)
     * @return SimulatorResult with success/failure, UTR, and latency
     */
    public SimulatorResult process(BigDecimal amount) {
        long latency = MIN_LATENCY_MS + random.nextInt(MAX_LATENCY_MS - MIN_LATENCY_MS + 1);

        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SimulatorResult.fail("UPI_GATEWAY_INTERRUPTED", latency);
        }

        boolean success = random.nextDouble() < SUCCESS_RATE;

        if (success) {
            String utr = generateUtr();
            log.info("UPI simulator: SUCCESS — UTR={}, amount=₹{}, latency={}ms", utr, amount, latency);
            return SimulatorResult.ok(utr, latency);
        } else {
            String reason = pickFailureReason();
            log.warn("UPI simulator: FAILED — reason={}, amount=₹{}, latency={}ms", reason, amount, latency);
            return SimulatorResult.fail(reason, latency);
        }
    }

    private String generateUtr() {
        String datePart = LocalDate.now().format(DATE_FMT);
        int randomPart = 100_000 + random.nextInt(900_000); // 6-digit random
        return "PAYS" + datePart + randomPart;
    }

    private String pickFailureReason() {
        String[] reasons = {
                "UPI_GATEWAY_TIMEOUT",
                "RECEIVER_BANK_UNAVAILABLE",
                "UPI_TRANSACTION_DECLINED",
                "NETWORK_ERROR"
        };
        return reasons[random.nextInt(reasons.length)];
    }
}
