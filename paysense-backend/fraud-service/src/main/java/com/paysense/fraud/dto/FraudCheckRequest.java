package com.paysense.fraud.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST request from Payment Service → Fraud Service for synchronous fraud check.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckRequest {

    @NotNull
    private UUID userId;

    @NotNull
    private UUID paymentRequestId;

    @NotNull
    private BigDecimal amount;

    @NotNull
    private String paymentType;

    private String receiverVpa;
}
