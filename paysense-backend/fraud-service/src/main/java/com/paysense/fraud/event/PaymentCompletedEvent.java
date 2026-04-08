package com.paysense.fraud.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Shared event — copy of payment-service's PaymentCompletedEvent.
 * Consumed from Kafka topic 'payment.completed'.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {

    private UUID paymentId;
    private UUID senderId;
    private UUID receiverId;
    private BigDecimal amount;
    private String paymentType;
    private String utrNumber;
    private String status;
    private LocalDateTime timestamp;
}
