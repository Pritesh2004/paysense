package com.paysense.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnusualSpendingResponse {
    private List<UnusualTransaction> unusualTransactions;
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnusualTransaction {
        private String category;
        private BigDecimal currentMonthSpend;
        private BigDecimal threeMonthAverage;
        private double multiplier;
        private String description;
        private LocalDateTime date;
    }
}
