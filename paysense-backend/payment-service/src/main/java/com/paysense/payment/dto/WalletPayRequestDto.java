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
public class WalletPayRequestDto {

    @NotBlank(message = "Receiver VPA is required")
    @Pattern(regexp = "^[\\w.-]+@[\\w.-]+$", message = "Invalid VPA format")
    private String receiverVpa;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum wallet payment is ₹1.00")
    @DecimalMax(value = "10000.00", message = "Maximum wallet payment per transaction is ₹10,000")
    private BigDecimal amount;

    @Size(max = 255)
    private String description;
}
