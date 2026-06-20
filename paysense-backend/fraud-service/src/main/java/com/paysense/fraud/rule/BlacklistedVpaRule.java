package com.paysense.fraud.rule;

import com.paysense.fraud.dto.FraudContext;
import com.paysense.fraud.repository.BlacklistedVpaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Blacklisted VPA Rule:
 *   If receiver VPA is in fraud.blacklisted_vpas table → BLOCKED immediately.
 *
 * Returns a score of 100 (which guarantees BLOCKED decision) to short-circuit the engine.
 */
@Component
@RequiredArgsConstructor
public class BlacklistedVpaRule implements FraudRule {

    private static final Logger log = LoggerFactory.getLogger(BlacklistedVpaRule.class);
    private static final int BLOCK_SCORE = 100;

    private final BlacklistedVpaRepository blacklistedVpaRepository;

    @Override
    public String getRuleName() {
        return "BLACKLISTED_VPA_RULE";
    }

    @Override
    public int getRiskScore(FraudContext context) {
        return evaluate(context) ? BLOCK_SCORE : 0;
    }

    @Override
    public boolean evaluate(FraudContext context) {
        if (context.getReceiverVpa() == null || context.getReceiverVpa().isBlank()) {
            return false;
        }

        boolean isBlacklisted = blacklistedVpaRepository.existsByVpa(context.getReceiverVpa());
        if (isBlacklisted) {
            log.warn("BlacklistedVpaRule BLOCKED: receiverVpa={}, userId={}",
                    context.getReceiverVpa(), context.getUserId());
        }
        return isBlacklisted;
    }
}
