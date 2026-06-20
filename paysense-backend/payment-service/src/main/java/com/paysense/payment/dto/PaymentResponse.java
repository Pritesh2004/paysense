package com.paysense.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private UUID paymentRequestId;
    private String idempotencyKey;
    private String utrNumber;
    private BigDecimal amount;
    private String paymentType;
    private String status;
    private String failureReason;
    private String description;
    private LocalDateTime initiatedAt;
    private LocalDateTime settledAt;
    private String message;
    private UUID senderAccountId;
    private String senderVpa;
    private String receiverVpa;
}
