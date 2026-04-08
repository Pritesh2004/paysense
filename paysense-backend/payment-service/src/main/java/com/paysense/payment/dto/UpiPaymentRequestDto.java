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
public class UpiPaymentRequestDto {

    @NotBlank(message = "Receiver VPA is required")
    private String receiverVpa;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum payment amount is ₹1.00")
    @DecimalMax(value = "500000.00", message = "Maximum UPI payment is ₹5,00,000")
    private BigDecimal amount;

    @Size(max = 255, message = "Description must be 255 characters or less")
    private String description;
}
