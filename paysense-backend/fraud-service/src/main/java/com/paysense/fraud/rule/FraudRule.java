package com.paysense.fraud.rule;

import com.paysense.fraud.dto.FraudContext;

/**
 * Interface for all fraud detection rules.
 *
 * Each rule evaluates a FraudContext and returns a risk score contribution.
 * Rules are injected as Spring @Component beans and evaluated by FraudRuleEngine.
 */
public interface FraudRule {

    /**
     * @return human-readable name of this rule (e.g., "HIGH_AMOUNT_RULE")
     */
    String getRuleName();

    /**
     * @return the risk score this rule contributes when triggered (0 if not triggered)
     */
    int getRiskScore(FraudContext context);

    /**
     * @return true if this rule is triggered for the given context
     */
    boolean evaluate(FraudContext context);
}
