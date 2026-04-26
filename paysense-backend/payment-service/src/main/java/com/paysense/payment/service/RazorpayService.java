package com.paysense.payment.service;

import com.paysense.payment.config.RazorpayConfig;
import com.paysense.payment.dto.PaymentResponse;
import com.paysense.payment.dto.RazorpayOrderRequest;
import com.paysense.payment.dto.RazorpayOrderResponse;
import com.paysense.payment.dto.RazorpayVerifyRequest;
import com.paysense.payment.entity.Account;
import com.paysense.payment.entity.PaymentRequest;
import com.paysense.payment.entity.Wallet;
import com.paysense.payment.event.PaymentEventPublisher;
import com.paysense.payment.exception.AccountNotFoundException;
import com.paysense.payment.exception.PaymentException;
import com.paysense.payment.repository.AccountRepository;
import com.paysense.payment.repository.PaymentRequestRepository;
import com.paysense.payment.repository.WalletRepository;
import com.paysense.payment.entity.VpaRegistry;
import com.paysense.payment.repository.VpaRegistryRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RazorpayService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayService.class);

    private final RazorpayClient razorpayClient;
    private final RazorpayConfig razorpayConfig;
    private final WalletRepository walletRepository;
    private final AccountRepository accountRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentEventPublisher eventPublisher;
    private final VpaRegistryRepository vpaRegistryRepository;

    @Transactional
    public RazorpayOrderResponse createOrder(UUID userId, RazorpayOrderRequest request) throws RazorpayException {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found for user: " + userId));
        
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new PaymentException("Wallet not found for user: " + userId));

        long amountInPaise = request.getAmount().multiply(new BigDecimal("100")).longValue();

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountInPaise);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "txn_" + UUID.randomUUID().toString().substring(0, 8));
        orderRequest.put("payment_capture", 1); 

        Order order = razorpayClient.orders.create(orderRequest);
        String razorpayOrderId = order.get("id");

        PaymentRequest pr = PaymentRequest.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .senderAccountId(account.getId())
                .amount(request.getAmount())
                .paymentType("RAZORPAY")
                .status("PENDING")
                .description("Wallet top-up via Razorpay")
                .razorpayOrderId(razorpayOrderId)
                .build();
        paymentRequestRepository.save(pr);

        log.info("Created Razorpay order: {} for amount: {} paise", razorpayOrderId, amountInPaise);

        return RazorpayOrderResponse.builder()
                .orderId(razorpayOrderId)
                .amountInPaise(amountInPaise)
                .currency("INR")
                .keyId(razorpayConfig.getKeyId())
                .transactionId(pr.getId())
                .build();
    }

    @Transactional
    public PaymentResponse verifyAndCreditWallet(RazorpayVerifyRequest verifyRequest) {
        PaymentRequest pr = paymentRequestRepository.findByRazorpayOrderId(verifyRequest.getRazorpayOrderId())
                .orElseThrow(() -> new PaymentException("Order not found: " + verifyRequest.getRazorpayOrderId()));

        if ("SUCCESS".equals(pr.getStatus())) {
            log.info("Order {} already successfully processed", verifyRequest.getRazorpayOrderId());
            return mapToResponse(pr, "Payment already successfully processed");
        }

        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", verifyRequest.getRazorpayOrderId());
            options.put("razorpay_payment_id", verifyRequest.getRazorpayPaymentId());
            options.put("razorpay_signature", verifyRequest.getRazorpaySignature());

            boolean isValid = Utils.verifyPaymentSignature(options, razorpayConfig.getKeySecret());

            if (isValid) {
                pr.setStatus("SUCCESS");
                pr.setRazorpayPaymentId(verifyRequest.getRazorpayPaymentId());
                pr.setRazorpaySignature(verifyRequest.getRazorpaySignature());
                pr.setSettledAt(LocalDateTime.now());
                pr.setUtrNumber("RZP" + verifyRequest.getRazorpayPaymentId().substring(0, Math.min(10, verifyRequest.getRazorpayPaymentId().length())));

                Account account = accountRepository.findById(pr.getSenderAccountId()).orElseThrow();
                Wallet wallet = walletRepository.findByUserId(account.getUserId())
                    .orElseThrow(() -> new PaymentException("Wallet not found"));

                wallet.setBalance(wallet.getBalance().add(pr.getAmount()));
                walletRepository.save(wallet);
                paymentRequestRepository.save(pr);

                eventPublisher.publishPaymentCompleted(pr, account.getUserId(), account.getId());

                log.info("Successfully verified payment and credited wallet for order: {}", verifyRequest.getRazorpayOrderId());
                return mapToResponse(pr, "Wallet top-up successful");
            } else {
                pr.setStatus("FAILED");
                pr.setFailureReason("Signature verification failed");
                paymentRequestRepository.save(pr);
                
                Account account = accountRepository.findById(pr.getSenderAccountId()).orElseThrow();
                eventPublisher.publishPaymentFailed(pr, account.getUserId());
                
                log.warn("Invalid signature for order: {}", verifyRequest.getRazorpayOrderId());
                throw new PaymentException("Invalid payment signature");
            }
        } catch (RazorpayException e) {
            log.error("Razorpay verification failed", e);
            throw new PaymentException("Failed to verify payment: " + e.getMessage());
        }
    }

    @Transactional
    public PaymentResponse handlePaymentFailure(String orderId, String reason) {
        PaymentRequest pr = paymentRequestRepository.findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new PaymentException("Order not found: " + orderId));

        if ("SUCCESS".equals(pr.getStatus())) {
            return mapToResponse(pr, "Payment already marked as successful");
        }

        pr.setStatus("FAILED");
        pr.setFailureReason(reason != null ? reason : "Payment failed at gateway");
        paymentRequestRepository.save(pr);

        Account account = accountRepository.findById(pr.getSenderAccountId()).orElseThrow();
        eventPublisher.publishPaymentFailed(pr, account.getUserId());

        log.info("Marked order {} as failed with reason: {}", orderId, reason);
        return mapToResponse(pr, "Payment failed: " + reason);
    }

    private PaymentResponse mapToResponse(PaymentRequest pr, String message) {
        String senderVpa = null;
        if (pr.getSenderAccountId() != null) {
            senderVpa = vpaRegistryRepository.findByAccountIdAndIsActiveTrue(pr.getSenderAccountId())
                    .stream().findFirst().map(VpaRegistry::getVpa).orElse(null);
        }
        return PaymentResponse.builder()
                .paymentRequestId(pr.getId())
                .utrNumber(pr.getUtrNumber())
                .amount(pr.getAmount())
                .paymentType(pr.getPaymentType())
                .status(pr.getStatus())
                .failureReason(pr.getFailureReason())
                .description(pr.getDescription())
                .initiatedAt(pr.getInitiatedAt())
                .settledAt(pr.getSettledAt())
                .message(message)
                .senderAccountId(pr.getSenderAccountId())
                .senderVpa(senderVpa)
                .receiverVpa(pr.getReceiverVpa())
                .build();
    }
}
