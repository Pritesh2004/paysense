package com.paysense.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for admin top-up endpoint — adds money to a user's account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTopupRequestDto {

    private UUID userId;
    private BigDecimal amount;
}
