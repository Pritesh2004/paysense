package com.paysense.payment.controller;

import com.paysense.payment.dto.PaymentResponse;
import com.paysense.payment.dto.RazorpayOrderRequest;
import com.paysense.payment.dto.RazorpayOrderResponse;
import com.paysense.payment.dto.RazorpayVerifyRequest;
import com.paysense.payment.service.RazorpayService;
import com.razorpay.RazorpayException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments/razorpay")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RazorpayController {

    private final RazorpayService razorpayService;

    @PostMapping("/create-order")
    public ResponseEntity<RazorpayOrderResponse> createOrder(
            @Valid @RequestBody RazorpayOrderRequest request,
            @RequestHeader("X-User-Id") UUID userId) throws RazorpayException {
        
        RazorpayOrderResponse response = razorpayService.createOrder(userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<PaymentResponse> verifyPayment(
            @Valid @RequestBody RazorpayVerifyRequest request) {
        
        PaymentResponse response = razorpayService.verifyAndCreditWallet(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/failure")
    public ResponseEntity<PaymentResponse> handleFailure(
            @RequestBody Map<String, String> payload) {
        
        String orderId = payload.get("orderId");
        String reason = payload.get("reason");
        PaymentResponse response = razorpayService.handlePaymentFailure(orderId, reason);
        return ResponseEntity.ok(response);
    }
}
