package com.paysense.fraud.service;

import com.paysense.fraud.dto.FraudCheckRequest;
import com.paysense.fraud.dto.FraudCheckResponse;
import com.paysense.fraud.dto.FraudContext;
import com.paysense.fraud.entity.FraudCheck;
import com.paysense.fraud.repository.FraudCheckRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Fraud Service — orchestrates fraud evaluation.
 *
 * Two integration points:
 *   1. Synchronous: called via REST by Payment Service before processing payment
 *   2. Asynchronous: Kafka consumer re-evaluates completed payments for ML scoring
 */
@Service
@RequiredArgsConstructor
public class FraudService {

    private static final Logger log = LoggerFactory.getLogger(FraudService.class);

    private final FraudRuleEngine ruleEngine;
    private final FraudCheckRepository fraudCheckRepository;
    private final PaymentServiceClient paymentServiceClient;

    /**
     * Synchronous fraud check — called by Payment Service before processing.
     */
    @Transactional
    public FraudCheckResponse checkFraud(FraudCheckRequest request) {
        log.info("Fraud check started: userId={}, paymentId={}, amount=₹{}, type={}",
                request.getUserId(), request.getPaymentRequestId(),
                request.getAmount(), request.getPaymentType());

        // Build context with REST-fetched velocity data
        int recentTxnCount = paymentServiceClient.getRecentTransactionCount(request.getUserId());

        FraudContext context = FraudContext.builder()
                .userId(request.getUserId())
                .paymentRequestId(request.getPaymentRequestId())
                .amount(request.getAmount())
                .paymentType(request.getPaymentType())
                .receiverVpa(request.getReceiverVpa())
                .timestamp(LocalDateTime.now())
                .recentTransactionCount(recentTxnCount)
                .build();

        // Evaluate all rules
        FraudCheckResponse response = ruleEngine.evaluate(context);

        // Persist result to fraud_checks table
        FraudCheck fraudCheck = FraudCheck.builder()
                .paymentRequestId(request.getPaymentRequestId())
                .userId(request.getUserId())
                .riskScore(response.getRiskScore())
                .decision(response.getDecision())
                .rulesTriggered(response.getRulesTriggered().toArray(new String[0]))
                .evaluatedAt(LocalDateTime.now())
                .build();
        fraudCheckRepository.save(fraudCheck);

        log.info("Fraud check result: paymentId={}, decision={}, score={}",
                request.getPaymentRequestId(), response.getDecision(), response.getRiskScore());

        return response;
    }

    /**
     * Async re-evaluation — called by Kafka consumer after payment is completed.
     * This is the "ML scoring" hook where additional analysis can be done post-payment.
     */
    @Transactional
    public FraudCheckResponse reevaluatePayment(FraudContext context) {
        log.info("Async fraud re-evaluation: paymentId={}, userId={}, amount=₹{}",
                context.getPaymentRequestId(), context.getUserId(), context.getAmount());

        FraudCheckResponse response = ruleEngine.evaluate(context);

        // Persist re-evaluation result
        FraudCheck fraudCheck = FraudCheck.builder()
                .paymentRequestId(context.getPaymentRequestId())
                .userId(context.getUserId())
                .riskScore(response.getRiskScore())
                .decision(response.getDecision())
                .rulesTriggered(response.getRulesTriggered().toArray(new String[0]))
                .evaluatedAt(LocalDateTime.now())
                .build();
        fraudCheckRepository.save(fraudCheck);

        return response;
    }
}
