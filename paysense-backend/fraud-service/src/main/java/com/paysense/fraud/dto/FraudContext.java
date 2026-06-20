package com.paysense.fraud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Context object passed to each FraudRule for evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudContext {

    private UUID userId;
    private UUID paymentRequestId;
    private BigDecimal amount;
    private String paymentType;
    private String receiverVpa;
    private LocalDateTime timestamp;

    /** Number of recent transactions in the last 10 minutes (populated by VelocityRule). */
    private int recentTransactionCount;
}
