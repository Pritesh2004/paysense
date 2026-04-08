package com.paysense.payment.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST client to Fraud Service with Resilience4j circuit breaker.
 *
 * If the Fraud Service is down, the circuit breaker opens and the fallback
 * method allows the payment through (with a logged warning), preventing
 * the fraud service outage from blocking all payments.
 */
@Service
public class FraudServiceClient {

    private static final Logger log = LoggerFactory.getLogger(FraudServiceClient.class);

    private final RestClient restClient;

    public FraudServiceClient(@Value("${app.fraud-service.url}") String fraudServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(fraudServiceUrl)
                .build();
    }

    /**
     * Checks a payment against the fraud detection engine.
     *
     * @return FraudCheckResult with decision (APPROVED / FLAGGED / BLOCKED) and risk score
     */
    @CircuitBreaker(name = "fraudService", fallbackMethod = "fraudCheckFallback")
    public FraudCheckResult checkFraud(UUID userId, UUID paymentRequestId, BigDecimal amount,
                                        String paymentType, String receiverVpa) {
        log.info("Calling fraud service for payment={}, userId={}, amount=₹{}", paymentRequestId, userId, amount);

        Map<String, Object> request = Map.of(
                "userId", userId.toString(),
                "paymentRequestId", paymentRequestId.toString(),
                "amount", amount,
                "paymentType", paymentType,
                "receiverVpa", receiverVpa != null ? receiverVpa : ""
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/api/fraud/check")
                .body(request)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            log.warn("Fraud service returned null response — allowing payment");
            return FraudCheckResult.approved();
        }

        String decision = (String) response.getOrDefault("decision", "APPROVED");
        int riskScore = (int) response.getOrDefault("riskScore", 0);
        @SuppressWarnings("unchecked")
        List<String> rulesTriggered = (List<String>) response.getOrDefault("rulesTriggered", List.of());

        log.info("Fraud check result: decision={}, riskScore={}, rules={}", decision, riskScore, rulesTriggered);
        return new FraudCheckResult(decision, riskScore, rulesTriggered);
    }

    /**
     * Fallback when fraud service is unavailable — allows payment through with a warning.
     */
    @SuppressWarnings("unused")
    private FraudCheckResult fraudCheckFallback(UUID userId, UUID paymentRequestId, BigDecimal amount,
                                                 String paymentType, String receiverVpa, Throwable t) {
        log.warn("Fraud service unavailable (circuit breaker open): {} — allowing payment for user={}",
                t.getMessage(), userId);
        return FraudCheckResult.approved();
    }

    /**
     * Inner record-like class for fraud check results.
     */
    public static class FraudCheckResult {
        private final String decision;
        private final int riskScore;
        private final List<String> rulesTriggered;

        public FraudCheckResult(String decision, int riskScore, List<String> rulesTriggered) {
            this.decision = decision;
            this.riskScore = riskScore;
            this.rulesTriggered = rulesTriggered;
        }

        public static FraudCheckResult approved() {
            return new FraudCheckResult("APPROVED", 0, List.of());
        }

        public String getDecision() { return decision; }
        public int getRiskScore() { return riskScore; }
        public List<String> getRulesTriggered() { return rulesTriggered; }

        public boolean isBlocked() { return "BLOCKED".equals(decision); }
        public boolean isFlagged() { return "FLAGGED".equals(decision); }
        public boolean isApproved() { return "APPROVED".equals(decision); }
    }
}
