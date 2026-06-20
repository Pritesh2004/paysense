package com.paysense.transaction.dto;

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
public class LedgerEntryResponse {
    private UUID id;
    private UUID paymentRequestId;
    private UUID accountId;
    private String entryType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String description;
    private String paymentType;
    private String category;
    private LocalDateTime createdAt;
}
