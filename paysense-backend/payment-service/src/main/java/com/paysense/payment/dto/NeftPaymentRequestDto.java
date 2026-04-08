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
public class NeftPaymentRequestDto {

    @NotBlank(message = "Receiver account number is required")
    @Size(max = 16, message = "Account number must be 16 characters or less")
    private String receiverAccountNo;

    @NotBlank(message = "Receiver IFSC code is required")
    @Size(max = 11, message = "IFSC code must be 11 characters or less")
    private String receiverIfsc;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum NEFT amount is ₹1.00")
    @DecimalMax(value = "1000000.00", message = "Maximum NEFT amount is ₹10,00,000")
    private BigDecimal amount;

    @Size(max = 255)
    private String description;
}
