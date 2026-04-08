package com.paysense.fraud.rule;

import com.paysense.fraud.dto.FraudContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * High Amount Rule:
 *   amount > 50,000  → +40 risk
 *   amount > 100,000 → +60 risk (replaces +40)
 */
@Component
public class HighAmountRule implements FraudRule {

    private static final Logger log = LoggerFactory.getLogger(HighAmountRule.class);
    private static final BigDecimal THRESHOLD_HIGH = new BigDecimal("50000");
    private static final BigDecimal THRESHOLD_VERY_HIGH = new BigDecimal("100000");

    @Override
    public String getRuleName() {
        return "HIGH_AMOUNT_RULE";
    }

    @Override
    public int getRiskScore(FraudContext context) {
        if (context.getAmount().compareTo(THRESHOLD_VERY_HIGH) > 0) {
            return 60;
        } else if (context.getAmount().compareTo(THRESHOLD_HIGH) > 0) {
            return 40;
        }
        return 0;
    }

    @Override
    public boolean evaluate(FraudContext context) {
        boolean triggered = context.getAmount().compareTo(THRESHOLD_HIGH) > 0;
        if (triggered) {
            log.info("HighAmountRule triggered: amount=₹{}, score={}",
                    context.getAmount(), getRiskScore(context));
        }
        return triggered;
    }
}
