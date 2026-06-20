package com.paysense.transaction.service;

import com.paysense.transaction.dto.*;
import com.paysense.transaction.entity.Budget;
import com.paysense.transaction.entity.LedgerEntry;
import com.paysense.transaction.repository.BudgetRepository;
import com.paysense.transaction.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Query service for transaction analytics — called by MCP Server tools.
 *
 * Note: Since this service operates in the "transaction" schema but accounts live
 * in the "payment" schema, we use accountId (which maps to user via payment.accounts.user_id).
 * The userId-to-accountId mapping is passed via request parameters from the caller
 * (the MCP Server or controller resolves userId → accountId).
 */
@Service
@RequiredArgsConstructor
public class TransactionQueryService {

    private static final Logger log = LoggerFactory.getLogger(TransactionQueryService.class);

    private final LedgerEntryRepository ledgerEntryRepository;
    private final BudgetRepository budgetRepository;

    /**
     * Get spending summary for a user in a given month/year.
     * Returns total spent and breakdown by category.
     *
     * Since we don't have direct access to payment.accounts, the controller
     * must provide the accountId derived from the JWT userId.
     */
    public SpendingSummaryResponse getSpendingSummary(UUID accountId, int month, int year) {
        LocalDateTime start = YearMonth.of(year, month).atDay(1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);

        BigDecimal totalSpent = ledgerEntryRepository.getTotalSpending(accountId, start, end);
        List<Object[]> categoryData = ledgerEntryRepository.getSpendingByCategory(accountId, start, end);

        List<SpendingSummaryResponse.CategoryBreakdown> breakdown = new ArrayList<>();
        for (Object[] row : categoryData) {
            String category = row[0] != null ? (String) row[0] : "UNCATEGORIZED";
            BigDecimal amount = (BigDecimal) row[1];
            double percentage = totalSpent.compareTo(BigDecimal.ZERO) > 0
                    ? amount.divide(totalSpent, 4, RoundingMode.HALF_UP).doubleValue() * 100
                    : 0.0;

            breakdown.add(SpendingSummaryResponse.CategoryBreakdown.builder()
                    .category(category)
                    .amount(amount)
                    .percentage(Math.round(percentage * 100.0) / 100.0)
                    .build());
        }

        // Sort by amount descending
        breakdown.sort((a, b) -> b.getAmount().compareTo(a.getAmount()));

        return SpendingSummaryResponse.builder()
                .totalSpent(totalSpent)
                .month(month)
                .year(year)
                .breakdown(breakdown)
                .build();
    }

    /**
     * Get recent transactions for a user, optionally filtered by category.
     */
    public Page<LedgerEntryResponse> getRecentTransactions(UUID accountId, int limit, String category) {
        Pageable pageable = PageRequest.of(0, limit);
        Page<LedgerEntry> entries = ledgerEntryRepository.findRecentByAccountAndCategory(
                accountId, category, pageable);

        return entries.map(this::mapToResponse);
    }

    /**
     * Compare budgets vs actual spending per category for a given month.
     */
    public BudgetStatusResponse getBudgetStatus(UUID userId, UUID accountId, int month, int year) {
        LocalDateTime start = YearMonth.of(year, month).atDay(1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);

        // Get all budgets for this user/month
        List<Budget> budgets = budgetRepository.findByUserIdAndMonthAndYear(userId, month, year);

        // Get actual spending by category
        List<Object[]> spendingData = ledgerEntryRepository.getSpendingByCategory(accountId, start, end);
        Map<String, BigDecimal> spendingMap = new HashMap<>();
        for (Object[] row : spendingData) {
            String category = row[0] != null ? (String) row[0] : "UNCATEGORIZED";
            spendingMap.put(category, (BigDecimal) row[1]);
        }

        List<BudgetStatusResponse.BudgetCategoryStatus> categories = new ArrayList<>();
        for (Budget budget : budgets) {
            BigDecimal actualSpend = spendingMap.getOrDefault(budget.getCategory(), BigDecimal.ZERO);
            double percentageUsed = budget.getAmount().compareTo(BigDecimal.ZERO) > 0
                    ? actualSpend.divide(budget.getAmount(), 4, RoundingMode.HALF_UP).doubleValue() * 100
                    : 0.0;

            categories.add(BudgetStatusResponse.BudgetCategoryStatus.builder()
                    .category(budget.getCategory())
                    .budgetLimit(budget.getAmount())
                    .actualSpend(actualSpend)
                    .percentageUsed(Math.round(percentageUsed * 100.0) / 100.0)
                    .overBudget(actualSpend.compareTo(budget.getAmount()) > 0)
                    .build());
        }

        return BudgetStatusResponse.builder()
                .month(month)
                .year(year)
                .categories(categories)
                .build();
    }

    /**
     * Detect unusual spending — transactions 2x above user's 3-month category average.
     */
    public UnusualSpendingResponse detectUnusualSpending(UUID accountId) {
        LocalDateTime now = LocalDateTime.now();
        YearMonth currentMonth = YearMonth.from(now);

        // Current month range
        LocalDateTime currentStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime currentEnd = currentStart.plusMonths(1);

        // 3-month lookback (excluding current month)
        LocalDateTime threeMonthsAgoStart = currentStart.minusMonths(3);

        // Get current month spending by category
        List<Object[]> currentSpending = ledgerEntryRepository.getSpendingByCategoryInRange(
                accountId, currentStart, currentEnd);

        // Get 3-month historical spending by category
        List<Object[]> historicalSpending = ledgerEntryRepository.getSpendingByCategoryInRange(
                accountId, threeMonthsAgoStart, currentStart);

        // Build 3-month average per category
        Map<String, BigDecimal> historicalAvgMap = new HashMap<>();
        for (Object[] row : historicalSpending) {
            String category = row[0] != null ? (String) row[0] : "UNCATEGORIZED";
            BigDecimal totalAmount = (BigDecimal) row[1];
            // Average over 3 months
            BigDecimal avg = totalAmount.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            historicalAvgMap.put(category, avg);
        }

        // Compare current month vs 3-month average
        List<UnusualSpendingResponse.UnusualTransaction> unusual = new ArrayList<>();
        for (Object[] row : currentSpending) {
            String category = row[0] != null ? (String) row[0] : "UNCATEGORIZED";
            BigDecimal currentAmount = (BigDecimal) row[1];
            BigDecimal avgAmount = historicalAvgMap.getOrDefault(category, BigDecimal.ZERO);

            if (avgAmount.compareTo(BigDecimal.ZERO) > 0) {
                double multiplier = currentAmount.divide(avgAmount, 2, RoundingMode.HALF_UP).doubleValue();
                if (multiplier >= 2.0) {
                    unusual.add(UnusualSpendingResponse.UnusualTransaction.builder()
                            .category(category)
                            .currentMonthSpend(currentAmount)
                            .threeMonthAverage(avgAmount)
                            .multiplier(multiplier)
                            .description("Spending in " + category + " is " +
                                    String.format("%.1fx", multiplier) + " above your 3-month average")
                            .date(now)
                            .build());
                }
            }
        }

        // Sort by multiplier descending
        unusual.sort((a, b) -> Double.compare(b.getMultiplier(), a.getMultiplier()));

        String message = unusual.isEmpty()
                ? "No unusual spending detected. Your spending is within normal patterns."
                : unusual.size() + " categor" + (unusual.size() == 1 ? "y" : "ies") +
                  " with unusual spending detected.";

        return UnusualSpendingResponse.builder()
                .unusualTransactions(unusual)
                .message(message)
                .build();
    }

    private LedgerEntryResponse mapToResponse(LedgerEntry entry) {
        return LedgerEntryResponse.builder()
                .id(entry.getId())
                .paymentRequestId(entry.getPaymentRequestId())
                .accountId(entry.getAccountId())
                .entryType(entry.getEntryType())
                .amount(entry.getAmount())
                .balanceAfter(entry.getBalanceAfter())
                .description(entry.getDescription())
                .paymentType(entry.getPaymentType())
                .category(entry.getCategory())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
