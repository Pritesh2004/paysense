package com.paysense.fraud.controller;

import com.paysense.fraud.dto.FraudCheckRequest;
import com.paysense.fraud.dto.FraudCheckResponse;
import com.paysense.fraud.entity.BlacklistedVpa;
import com.paysense.fraud.entity.FraudCheck;
import com.paysense.fraud.repository.BlacklistedVpaRepository;
import com.paysense.fraud.repository.FraudCheckRepository;
import com.paysense.fraud.service.FraudService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
public class FraudController {

    private final FraudService fraudService;
    private final FraudCheckRepository fraudCheckRepository;
    private final BlacklistedVpaRepository blacklistedVpaRepository;

    /**
     * Synchronous fraud check — called by Payment Service before processing payment.
     * Protected by @CircuitBreaker on the caller side (payment-service).
     */
    @PostMapping("/check")
    public ResponseEntity<FraudCheckResponse> checkFraud(@Valid @RequestBody FraudCheckRequest request) {
        FraudCheckResponse response = fraudService.checkFraud(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get fraud check history for a user.
     */
    @GetMapping("/history/{userId}")
    public ResponseEntity<List<FraudCheck>> getFraudHistory(@PathVariable UUID userId) {
        return ResponseEntity.ok(fraudCheckRepository.findByUserId(userId));
    }

    /**
     * Get fraud check for a specific payment.
     */
    @GetMapping("/payment/{paymentId}")
    public ResponseEntity<List<FraudCheck>> getFraudCheckByPayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(fraudCheckRepository.findByPaymentRequestId(paymentId));
    }

    // ── Blacklist Management ────────────────────────────────

    /**
     * Add a VPA to the blacklist.
     */
    @PostMapping("/blacklist")
    public ResponseEntity<BlacklistedVpa> addToBlacklist(@RequestBody Map<String, String> request) {
        String vpa = request.get("vpa");
        String reason = request.getOrDefault("reason", "Manual blacklist");

        if (blacklistedVpaRepository.existsByVpa(vpa)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        BlacklistedVpa entry = BlacklistedVpa.builder()
                .vpa(vpa)
                .reason(reason)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(blacklistedVpaRepository.save(entry));
    }

    /**
     * Get all blacklisted VPAs.
     */
    @GetMapping("/blacklist")
    public ResponseEntity<List<BlacklistedVpa>> getBlacklist() {
        return ResponseEntity.ok(blacklistedVpaRepository.findAll());
    }
}
