package com.paysense.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetResponse {
    private UUID id;
    private UUID userId;
    private String category;
    private BigDecimal amount;
    private int month;
    private int year;
    private String message;
}
