package com.paysense.transaction.service;

import com.paysense.transaction.dto.BudgetRequest;
import com.paysense.transaction.dto.BudgetResponse;
import com.paysense.transaction.entity.Budget;
import com.paysense.transaction.repository.BudgetRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages monthly budget limits per category for users.
 * Supports upsert behavior — creates new or updates existing budget.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);
    private final BudgetRepository budgetRepository;

    /**
     * Set or update a monthly budget for a category.
     * Upsert based on unique constraint: (userId, category, month, year).
     */
    @Transactional
    public BudgetResponse setOrUpdateBudget(BudgetRequest request) {
        Optional<Budget> existing = budgetRepository.findByUserIdAndCategoryAndMonthAndYear(
                request.getUserId(),
                request.getCategory().toUpperCase(),
                request.getMonth(),
                request.getYear()
        );

        Budget budget;
        String message;

        if (existing.isPresent()) {
            budget = existing.get();
            budget.setAmount(request.getAmount());
            budget = budgetRepository.save(budget);
            message = "Budget updated successfully";
            log.info("Budget updated: userId={}, category={}, amount={}, month={}/{}",
                    request.getUserId(), request.getCategory(), request.getAmount(),
                    request.getMonth(), request.getYear());
        } else {
            budget = Budget.builder()
                    .userId(request.getUserId())
                    .category(request.getCategory().toUpperCase())
                    .amount(request.getAmount())
                    .month(request.getMonth())
                    .year(request.getYear())
                    .build();
            budget = budgetRepository.save(budget);
            message = "Budget created successfully";
            log.info("Budget created: userId={}, category={}, amount={}, month={}/{}",
                    request.getUserId(), request.getCategory(), request.getAmount(),
                    request.getMonth(), request.getYear());
        }

        return BudgetResponse.builder()
                .id(budget.getId())
                .userId(budget.getUserId())
                .category(budget.getCategory())
                .amount(budget.getAmount())
                .month(budget.getMonth())
                .year(budget.getYear())
                .message(message)
                .build();
    }

    /**
     * Get all budgets for a user in a specific month/year.
     */
    public List<BudgetResponse> getBudgets(UUID userId, int month, int year) {
        return budgetRepository.findByUserIdAndMonthAndYear(userId, month, year)
                .stream()
                .map(b -> BudgetResponse.builder()
                        .id(b.getId())
                        .userId(b.getUserId())
                        .category(b.getCategory())
                        .amount(b.getAmount())
                        .month(b.getMonth())
                        .year(b.getYear())
                        .build())
                .toList();
    }
}
