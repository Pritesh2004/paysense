package com.paysense.notification.consumer;

import com.paysense.notification.event.FraudAlertEvent;
import com.paysense.notification.event.PaymentEvent;
import com.paysense.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "payment.completed", groupId = "notification-group")
    public void handlePaymentCompleted(PaymentEvent event) {
        log.info("Received payment.completed event: {}", event);
        
        String title = "Payment Successful";
        String body = String.format("Your payment of %s to %s was successful. UTR: %s", 
                event.getAmount(), 
                event.getReceiverVpa() != null ? event.getReceiverVpa() : "Account " + event.getReceiverAccountNo(),
                event.getUtrNumber());
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("paymentRequestId", event.getPaymentRequestId().toString());
        metadata.put("amount", event.getAmount());
        metadata.put("utrNumber", event.getUtrNumber());
        
        notificationService.createAndSendNotification(
                event.getSenderUserId(),
                "PAYMENT_SUCCESS",
                title,
                body,
                "IN_APP",
                metadata
        );
    }

    @KafkaListener(topics = "payment.failed", groupId = "notification-group")
    public void handlePaymentFailed(PaymentEvent event) {
        log.info("Received payment.failed event: {}", event);
        
        String title = "Payment Failed";
        String body = String.format("Your payment of %s failed. Reason: %s", 
                event.getAmount(), 
                event.getFailureReason());
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("paymentRequestId", event.getPaymentRequestId().toString());
        metadata.put("amount", event.getAmount());
        metadata.put("failureReason", event.getFailureReason());
        
        notificationService.createAndSendNotification(
                event.getSenderUserId(),
                "PAYMENT_FAILED",
                title,
                body,
                "IN_APP",
                metadata
        );
    }

    @KafkaListener(topics = "fraud.alert", groupId = "notification-group")
    public void handleFraudAlert(FraudAlertEvent event) {
        log.info("Received fraud.alert event: {}", event);
        
        String title = "Security Alert";
        String body = String.format("We detected suspicious activity on your recent payment (Score: %d). Action taken: %s", 
                event.getRiskScore(), 
                event.getDecision());
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("paymentId", event.getPaymentId().toString());
        metadata.put("riskScore", event.getRiskScore());
        metadata.put("decision", event.getDecision());
        metadata.put("rulesTriggered", event.getRulesTriggered());
        
        notificationService.createAndSendNotification(
                event.getUserId(),
                "FRAUD_ALERT",
                title,
                body,
                "IN_APP",
                metadata
        );
    }
}
