package com.paysense.fraud.service;

import com.paysense.fraud.dto.FraudCheckResponse;
import com.paysense.fraud.dto.FraudContext;
import com.paysense.fraud.rule.FraudRule;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fraud Rule Engine — evaluates all registered FraudRule beans against a FraudContext.
 *
 * Decision logic:
 *   score >= 70  → BLOCKED
 *   score 40-69  → FLAGGED
 *   score <  40  → APPROVED
 */
@Component
@RequiredArgsConstructor
public class FraudRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(FraudRuleEngine.class);

    private final List<FraudRule> fraudRules;

    /**
     * Evaluate all fraud rules against the given context.
     *
     * @param context the transaction context to evaluate
     * @return FraudCheckResponse with decision, score, and triggered rules
     */
    public FraudCheckResponse evaluate(FraudContext context) {
        int totalRiskScore = 0;
        List<String> triggeredRules = new ArrayList<>();

        for (FraudRule rule : fraudRules) {
            if (rule.evaluate(context)) {
                int ruleScore = rule.getRiskScore(context);
                totalRiskScore += ruleScore;
                triggeredRules.add(rule.getRuleName());
                log.info("Rule triggered: {} → +{} risk (total: {})",
                        rule.getRuleName(), ruleScore, totalRiskScore);
            }
        }

        String decision = determineDecision(totalRiskScore);

        log.info("Fraud evaluation complete: userId={}, paymentId={}, score={}, decision={}, rules={}",
                context.getUserId(), context.getPaymentRequestId(),
                totalRiskScore, decision, triggeredRules);

        return FraudCheckResponse.builder()
                .decision(decision)
                .riskScore(totalRiskScore)
                .rulesTriggered(triggeredRules)
                .build();
    }

    private String determineDecision(int score) {
        if (score >= 70) return "BLOCKED";
        if (score >= 40) return "FLAGGED";
        return "APPROVED";
    }
}
