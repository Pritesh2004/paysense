package com.paysense.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpendingSummaryResponse {
    private BigDecimal totalSpent;
    private int month;
    private int year;
    private List<CategoryBreakdown> breakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryBreakdown {
        private String category;
        private BigDecimal amount;
        private double percentage;
    }
}
