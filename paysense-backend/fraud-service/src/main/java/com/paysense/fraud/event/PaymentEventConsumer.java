package com.paysense.fraud.event;

import com.paysense.fraud.dto.FraudCheckResponse;
import com.paysense.fraud.dto.FraudContext;
import com.paysense.fraud.service.FraudService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Kafka consumer for 'payment.completed' topic.
 *
 * Performs async re-evaluation of completed payments.
 * If fraud is detected post-payment, publishes FraudAlertEvent to 'fraud.alert' topic.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private static final String FRAUD_ALERT_TOPIC = "fraud.alert";

    private final FraudService fraudService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(
            topics = "payment.completed",
            groupId = "fraud-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received payment.completed event: paymentId={}, senderId={}, amount=₹{}",
                event.getPaymentId(), event.getSenderId(), event.getAmount());

        try {
            // Build context from Kafka event
            FraudContext context = FraudContext.builder()
                    .userId(event.getSenderId())
                    .paymentRequestId(event.getPaymentId())
                    .amount(event.getAmount())
                    .paymentType(event.getPaymentType())
                    .receiverVpa(null) // not available in payment.completed event
                    .timestamp(event.getTimestamp())
                    .recentTransactionCount(0) // skip REST call in async mode
                    .build();

            // Async re-evaluation
            FraudCheckResponse result = fraudService.reevaluatePayment(context);

            // If flagged or blocked, publish FraudAlertEvent
            if (!"APPROVED".equals(result.getDecision())) {
                FraudAlertEvent alert = FraudAlertEvent.builder()
                        .paymentId(event.getPaymentId())
                        .userId(event.getSenderId())
                        .riskScore(result.getRiskScore())
                        .decision(result.getDecision())
                        .rulesTriggered(result.getRulesTriggered())
                        .timestamp(LocalDateTime.now())
                        .build();

                kafkaTemplate.send(FRAUD_ALERT_TOPIC, event.getPaymentId().toString(), alert)
                        .whenComplete((res, ex) -> {
                            if (ex != null) {
                                log.error("Failed to publish fraud alert: {}", ex.getMessage());
                            } else {
                                log.warn("Published FraudAlertEvent: paymentId={}, decision={}, score={}",
                                        event.getPaymentId(), result.getDecision(), result.getRiskScore());
                            }
                        });
            }
        } catch (Exception e) {
            log.error("Error processing payment.completed event: paymentId={}, error={}",
                    event.getPaymentId(), e.getMessage(), e);
            throw e; // re-throw for DefaultErrorHandler to handle retries + DLT
        }
    }
}
