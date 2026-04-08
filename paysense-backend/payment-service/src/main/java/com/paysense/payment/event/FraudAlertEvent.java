package com.paysense.payment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Shared Kafka event — published by Fraud Service when fraud is detected post-payment.
 * Consumed by Notification Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlertEvent {

    private UUID paymentId;
    private UUID userId;
    private int riskScore;
    private String decision;
    private List<String> rulesTriggered;
    private LocalDateTime timestamp;
}
