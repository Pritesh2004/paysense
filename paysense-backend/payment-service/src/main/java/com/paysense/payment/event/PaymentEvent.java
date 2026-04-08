package com.paysense.payment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka event payload published to payment.completed / payment.failed topics.
 * Consumed by Fraud Service, Notification Service, and Transaction Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {

    private UUID paymentRequestId;
    private UUID senderUserId;
    private UUID senderAccountId;
    private UUID receiverAccountId;
    private String receiverVpa;
    private String receiverAccountNo;
    private BigDecimal amount;
    private String paymentType;
    private String status;
    private String utrNumber;
    private String failureReason;
    private String description;
    private LocalDateTime timestamp;
}
