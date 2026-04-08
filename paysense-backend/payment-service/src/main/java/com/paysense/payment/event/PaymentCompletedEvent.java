package com.paysense.payment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Shared event — published to Kafka on payment completion.
 * Copy this class into every consuming service (fraud-service, notification-service, transaction-service).
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
