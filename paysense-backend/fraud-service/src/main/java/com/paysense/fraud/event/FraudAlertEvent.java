package com.paysense.fraud.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Published to Kafka topic 'fraud.alert' when post-payment fraud is detected.
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
