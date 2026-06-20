package com.paysense.transaction.controller;

import com.paysense.transaction.dto.CreateLedgerRequest;
import com.paysense.transaction.dto.LedgerEntryResponse;
import com.paysense.transaction.service.LedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Internal endpoint for service-to-service communication.
 * No JWT required — secured by network policy (only accessible from other services).
 * Payment Service calls this after a successful payment to create ledger entries.
 */
@RestController
@RequestMapping("/internal/ledger")
@RequiredArgsConstructor
public class InternalLedgerController {

    private final LedgerService ledgerService;

    /**
     * Create double-entry ledger pair — called by Payment Service.
     */
    @PostMapping("/double-entry")
    public ResponseEntity<List<LedgerEntryResponse>> createDoubleEntry(
            @Valid @RequestBody CreateLedgerRequest request) {
        List<LedgerEntryResponse> entries = ledgerService.createDoubleEntry(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(entries);
    }
}
