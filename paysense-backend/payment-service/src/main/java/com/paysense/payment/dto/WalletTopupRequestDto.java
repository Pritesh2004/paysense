package com.paysense.payment.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTopupRequestDto {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum top-up is ₹1.00")
    @DecimalMax(value = "100000.00", message = "Maximum top-up is ₹1,00,000")
    private BigDecimal amount;
}
