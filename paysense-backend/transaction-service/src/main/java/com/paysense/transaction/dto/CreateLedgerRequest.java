package com.paysense.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request to create a double-entry ledger pair.
 * Called by Payment Service after a successful payment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLedgerRequest {

    @NotNull(message = "Payment request ID is required")
    private UUID paymentRequestId;

    @NotNull(message = "Sender account ID is required")
    private UUID senderAccountId;

    @NotNull(message = "Receiver account ID is required")
    private UUID receiverAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    private String type; // UPI, NEFT, WALLET, TOPUP

    private String description;

    private String category; // FOOD, TRANSPORT, etc.

    /** Sender's balance AFTER debit */
    private BigDecimal senderBalanceAfter;

    /** Receiver's balance AFTER credit */
    private BigDecimal receiverBalanceAfter;
}
