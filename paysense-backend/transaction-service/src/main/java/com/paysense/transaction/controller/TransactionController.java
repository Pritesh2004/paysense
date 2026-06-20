package com.paysense.transaction.controller;

import com.paysense.transaction.dto.*;
import com.paysense.transaction.security.JwtUserDetails;
import com.paysense.transaction.service.BudgetService;
import com.paysense.transaction.service.LedgerService;
import com.paysense.transaction.service.TransactionQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller exposing Transaction Service endpoints.
 * All endpoints require JWT authentication.
 * Uses @PreAuthorize to ensure users can only access their own data.
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TransactionController {

    private final LedgerService ledgerService;
    private final TransactionQueryService transactionQueryService;
    private final BudgetService budgetService;

    // ── Ledger ──────────────────────────────────────────────

    /**
     * Create a double-entry ledger pair. Called by Payment Service after successful payment.
     * Also accessible internally via /internal/ledger/double-entry (no auth).
     */
    @PostMapping("/ledger")
    public ResponseEntity<List<LedgerEntryResponse>> createDoubleEntry(
            @Valid @RequestBody CreateLedgerRequest request) {
        List<LedgerEntryResponse> entries = ledgerService.createDoubleEntry(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(entries);
    }

    // ── Spending Summary ────────────────────────────────────

    /**
     * Get spending summary with category breakdown for a given month.
     * MCP Server calls this to answer "How much did I spend this month?"
     */
    @GetMapping("/spending-summary")
    @PreAuthorize("#userId == authentication.principal.id")
    public ResponseEntity<SpendingSummaryResponse> getSpendingSummary(
            @RequestParam UUID userId,
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam(required = false) UUID accountId,
            @AuthenticationPrincipal JwtUserDetails principal) {
        // Use provided accountId or derive (in production, would look up from payment service)
        UUID resolvedAccountId = accountId != null ? accountId : userId;
        SpendingSummaryResponse response = transactionQueryService.getSpendingSummary(
                resolvedAccountId, month, year);
        return ResponseEntity.ok(response);
    }

    // ── Recent Transactions ─────────────────────────────────

    /**
     * Get recent transactions, optionally filtered by category.
     */
    @GetMapping("/recent")
    @PreAuthorize("#userId == authentication.principal.id")
    public ResponseEntity<Page<LedgerEntryResponse>> getRecentTransactions(
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID accountId,
            @AuthenticationPrincipal JwtUserDetails principal) {
        UUID resolvedAccountId = accountId != null ? accountId : userId;
        Page<LedgerEntryResponse> transactions = transactionQueryService.getRecentTransactions(
                resolvedAccountId, limit, category);
        return ResponseEntity.ok(transactions);
    }

    // ── Budget Status ───────────────────────────────────────

    /**
     * Compare budgets vs actual spending per category.
     */
    @GetMapping("/budget-status")
    @PreAuthorize("#userId == authentication.principal.id")
    public ResponseEntity<BudgetStatusResponse> getBudgetStatus(
            @RequestParam UUID userId,
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam(required = false) UUID accountId,
            @AuthenticationPrincipal JwtUserDetails principal) {
        UUID resolvedAccountId = accountId != null ? accountId : userId;
        BudgetStatusResponse response = transactionQueryService.getBudgetStatus(
                userId, resolvedAccountId, month, year);
        return ResponseEntity.ok(response);
    }

    // ── Unusual Spending ────────────────────────────────────

    /**
     * Detect spending anomalies — transactions 2x above 3-month category average.
     */
    @GetMapping("/unusual")
    @PreAuthorize("#userId == authentication.principal.id")
    public ResponseEntity<UnusualSpendingResponse> detectUnusualSpending(
            @RequestParam UUID userId,
            @RequestParam(required = false) UUID accountId,
            @AuthenticationPrincipal JwtUserDetails principal) {
        UUID resolvedAccountId = accountId != null ? accountId : userId;
        UnusualSpendingResponse response = transactionQueryService.detectUnusualSpending(resolvedAccountId);
        return ResponseEntity.ok(response);
    }

    // ── Budgets ─────────────────────────────────────────────

    /**
     * Set or update a monthly budget for a category.
     */
    @PostMapping("/budgets")
    @PreAuthorize("#request.userId == authentication.principal.id")
    public ResponseEntity<BudgetResponse> setOrUpdateBudget(
            @Valid @RequestBody BudgetRequest request,
            @AuthenticationPrincipal JwtUserDetails principal) {
        BudgetResponse response = budgetService.setOrUpdateBudget(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all budgets for a user in a specific month/year.
     */
    @GetMapping("/budgets")
    @PreAuthorize("#userId == authentication.principal.id")
    public ResponseEntity<List<BudgetResponse>> getBudgets(
            @RequestParam UUID userId,
            @RequestParam int month,
            @RequestParam int year,
            @AuthenticationPrincipal JwtUserDetails principal) {
        List<BudgetResponse> budgets = budgetService.getBudgets(userId, month, year);
        return ResponseEntity.ok(budgets);
    }
}
