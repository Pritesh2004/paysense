package com.paysense.fraud.rule;

import com.paysense.fraud.dto.FraudContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Unusual Time Rule:
 *   Transaction between 23:00 and 05:00 → +30 risk
 */
@Component
public class UnusualTimeRule implements FraudRule {

    private static final Logger log = LoggerFactory.getLogger(UnusualTimeRule.class);
    private static final LocalTime NIGHT_START = LocalTime.of(23, 0);
    private static final LocalTime NIGHT_END = LocalTime.of(5, 0);

    @Override
    public String getRuleName() {
        return "UNUSUAL_TIME_RULE";
    }

    @Override
    public int getRiskScore(FraudContext context) {
        return evaluate(context) ? 30 : 0;
    }

    @Override
    public boolean evaluate(FraudContext context) {
        LocalDateTime timestamp = context.getTimestamp() != null
                ? context.getTimestamp()
                : LocalDateTime.now();

        LocalTime txTime = timestamp.toLocalTime();
        boolean isUnusual = txTime.isAfter(NIGHT_START) || txTime.isBefore(NIGHT_END);

        if (isUnusual) {
            log.info("UnusualTimeRule triggered: time={}, score=30", txTime);
        }
        return isUnusual;
    }
}
