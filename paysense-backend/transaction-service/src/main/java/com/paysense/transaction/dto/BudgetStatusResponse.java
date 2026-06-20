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
public class BudgetStatusResponse {
    private int month;
    private int year;
    private List<BudgetCategoryStatus> categories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetCategoryStatus {
        private String category;
        private BigDecimal budgetLimit;
        private BigDecimal actualSpend;
        private double percentageUsed;
        private boolean overBudget;
    }
}
