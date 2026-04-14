package com.paysense.payment.controller;

import com.paysense.payment.dto.*;
import com.paysense.payment.service.PaymentService;
import com.paysense.payment.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentService paymentService;
    private final WalletService walletService;

    // ── UPI ─────────────────────────────────────────────────

    /**
     * Process a UPI payment.
     *
     * @param request        UPI payment details (receiverVpa, amount, description)
     * @param idempotencyKey Unique client-generated key to prevent duplicates
     * @param userId         Sender's userId (from JWT in production; header for now)
     */
    @PostMapping("/upi")
    public ResponseEntity<PaymentResponse> processUpiPayment(
            @Valid @RequestBody UpiPaymentRequestDto request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") UUID userId) {

        PaymentResponse response = paymentService.processUpiPayment(request, idempotencyKey, userId);
        HttpStatus status = "SUCCESS".equals(response.getStatus()) ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }

    // ── NEFT ────────────────────────────────────────────────

    /**
     * Initiate a NEFT payment (batch settlement within 30 minutes).
     */
    @PostMapping("/neft")
    public ResponseEntity<PaymentResponse> initiateNeftPayment(
            @Valid @RequestBody NeftPaymentRequestDto request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") UUID userId) {

        PaymentResponse response = paymentService.initiateNeftPayment(request, idempotencyKey, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // ── Wallet ──────────────────────────────────────────────

    /**
     * Top-up wallet from account balance.
     */
    @PostMapping("/wallet/topup")
    public ResponseEntity<PaymentResponse> topupWallet(
            @Valid @RequestBody WalletTopupRequestDto request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") UUID userId) {

        PaymentResponse response = walletService.topupWallet(userId, request.getAmount(), idempotencyKey);
        return ResponseEntity.ok(response);
    }

    /**
     * Pay from wallet to another user (via VPA).
     */
    @PostMapping("/wallet/pay")
    public ResponseEntity<PaymentResponse> walletPay(
            @Valid @RequestBody WalletPayRequestDto request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") UUID userId) {

        PaymentResponse response = walletService.walletPay(
                userId, request.getReceiverVpa(), request.getAmount(),
                request.getDescription(), idempotencyKey);
        return ResponseEntity.ok(response);
    }

    // ── History ─────────────────────────────────────────────

    /**
     * Get payment history for a user.
     */
    @GetMapping("/history")
    public ResponseEntity<List<PaymentResponse>> getPaymentHistory(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(paymentService.getPaymentHistory(userId));
    }

    /**
     * Get a specific payment by ID.
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.getPaymentById(paymentId));
    }
}
