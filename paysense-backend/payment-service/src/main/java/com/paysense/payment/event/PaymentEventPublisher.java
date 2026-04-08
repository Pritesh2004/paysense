package com.paysense.payment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysense.payment.entity.PaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publishes payment events to Kafka topics.
 *
 * Topics:
 *   - payment.completed : successful payments
 *   - payment.failed    : failed payments
 *   - payment.initiated : payment initiation (for fraud async check)
 */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);
    private static final String TOPIC_COMPLETED = "payment.completed";
    private static final String TOPIC_FAILED = "payment.failed";
    private static final String TOPIC_INITIATED = "payment.initiated";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaymentEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishPaymentCompleted(PaymentRequest pr, UUID senderUserId, UUID receiverAccountId) {
        PaymentEvent event = buildEvent(pr, senderUserId, receiverAccountId);
        publishEvent(TOPIC_COMPLETED, pr.getId().toString(), event);
    }

    public void publishPaymentFailed(PaymentRequest pr, UUID senderUserId) {
        PaymentEvent event = buildEvent(pr, senderUserId, null);
        publishEvent(TOPIC_FAILED, pr.getId().toString(), event);
    }

    public void publishPaymentInitiated(PaymentRequest pr, UUID senderUserId) {
        PaymentEvent event = buildEvent(pr, senderUserId, null);
        publishEvent(TOPIC_INITIATED, pr.getId().toString(), event);
    }

    private PaymentEvent buildEvent(PaymentRequest pr, UUID senderUserId, UUID receiverAccountId) {
        return PaymentEvent.builder()
                .paymentRequestId(pr.getId())
                .senderUserId(senderUserId)
                .senderAccountId(pr.getSenderAccountId())
                .receiverAccountId(receiverAccountId)
                .receiverVpa(pr.getReceiverVpa())
                .receiverAccountNo(pr.getReceiverAccountNo())
                .amount(pr.getAmount())
                .paymentType(pr.getPaymentType())
                .status(pr.getStatus())
                .utrNumber(pr.getUtrNumber())
                .failureReason(pr.getFailureReason())
                .description(pr.getDescription())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private void publishEvent(String topic, String key, PaymentEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish event to topic={}, key={}: {}",
                                    topic, key, ex.getMessage());
                        } else {
                            log.info("Published event to topic={}, key={}, partition={}, offset={}",
                                    topic, key,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to serialize payment event for topic={}: {}", topic, e.getMessage());
        }
    }
}
