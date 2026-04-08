package com.paysense.payment.event;

import com.paysense.payment.entity.PaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka producer service — publishes typed events using JsonSerializer.
 *
 * Topics:
 *   - payment.completed : successful payments (consumed by notification, transaction services)
 *   - payment.failed    : failed payments (consumed by notification service)
 *   - payment.initiated : payment initiation (consumed by fraud service for async check)
 */
@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private static final String TOPIC_COMPLETED = "payment.completed";
    private static final String TOPIC_FAILED = "payment.failed";
    private static final String TOPIC_INITIATED = "payment.initiated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPaymentCompleted(PaymentRequest pr, UUID senderUserId, UUID receiverAccountId) {
        PaymentCompletedEvent event = buildEvent(pr, senderUserId, receiverAccountId, "SUCCESS");
        publishEvent(TOPIC_COMPLETED, pr.getId().toString(), event);
    }

    public void publishPaymentFailed(PaymentRequest pr, UUID senderUserId) {
        PaymentCompletedEvent event = buildEvent(pr, senderUserId, null, "FAILED");
        publishEvent(TOPIC_FAILED, pr.getId().toString(), event);
    }

    public void publishPaymentInitiated(PaymentRequest pr, UUID senderUserId) {
        PaymentCompletedEvent event = buildEvent(pr, senderUserId, null, "INITIATED");
        publishEvent(TOPIC_INITIATED, pr.getId().toString(), event);
    }

    private PaymentCompletedEvent buildEvent(PaymentRequest pr, UUID senderUserId,
                                              UUID receiverAccountId, String status) {
        return PaymentCompletedEvent.builder()
                .paymentId(pr.getId())
                .senderId(senderUserId)
                .receiverId(receiverAccountId)
                .amount(pr.getAmount())
                .paymentType(pr.getPaymentType())
                .utrNumber(pr.getUtrNumber())
                .status(status)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private void publishEvent(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event)
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
    }
}
