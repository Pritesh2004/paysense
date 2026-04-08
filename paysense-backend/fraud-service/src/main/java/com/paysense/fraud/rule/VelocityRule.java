package com.paysense.fraud.rule;

import com.paysense.fraud.dto.FraudContext;
import com.paysense.fraud.repository.FraudCheckRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Velocity Rule:
 *   More than 5 transactions in 10 minutes from the same account → +50 risk
 *
 * Uses fraud_checks table count (how many fraud evaluations in the last 10 min
 * for this user) as a proxy for transaction velocity.
 * Additionally calls Payment Service REST API (via recentTransactionCount in FraudContext).
 */
@Component
@RequiredArgsConstructor
public class VelocityRule implements FraudRule {

    private static final Logger log = LoggerFactory.getLogger(VelocityRule.class);
    private static final int MAX_TRANSACTIONS_IN_WINDOW = 5;
    private static final int WINDOW_MINUTES = 10;
    private static final int RISK_SCORE = 50;

    private final FraudCheckRepository fraudCheckRepository;

    @Override
    public String getRuleName() {
        return "VELOCITY_RULE";
    }

    @Override
    public int getRiskScore(FraudContext context) {
        return evaluate(context) ? RISK_SCORE : 0;
    }

    @Override
    public boolean evaluate(FraudContext context) {
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(WINDOW_MINUTES);

        // Count fraud checks in the last 10 minutes for this user (local DB)
        long recentChecks = fraudCheckRepository.countByUserIdAndEvaluatedAtAfter(
                context.getUserId(), windowStart);

        // Also consider the count passed from external payment-service call
        long totalRecent = recentChecks + context.getRecentTransactionCount();

        boolean triggered = totalRecent > MAX_TRANSACTIONS_IN_WINDOW;
        if (triggered) {
            log.warn("VelocityRule triggered: userId={}, recentTxn={} ({}+{}) in {}min, score={}",
                    context.getUserId(), totalRecent, recentChecks,
                    context.getRecentTransactionCount(), WINDOW_MINUTES, RISK_SCORE);
        }
        return triggered;
    }
}
