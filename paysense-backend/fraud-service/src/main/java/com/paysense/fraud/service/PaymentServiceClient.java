package com.paysense.fraud.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST client to Payment Service — used by VelocityRule to count recent transactions.
 */
@Service
public class PaymentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceClient.class);

    private final RestClient restClient;

    public PaymentServiceClient(@Value("${app.payment-service.url}") String paymentServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(paymentServiceUrl)
                .build();
    }

    /**
     * Get the count of recent payments for a user (from payment-service).
     * Falls back to 0 if the call fails.
     */
    public int getRecentTransactionCount(UUID userId) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> payments = restClient.get()
                    .uri("/api/payments/history")
                    .header("X-User-Id", userId.toString())
                    .retrieve()
                    .body(List.class);

            if (payments != null) {
                return payments.size();
            }
        } catch (Exception e) {
            log.warn("Could not fetch recent transactions for user={}: {}", userId, e.getMessage());
        }
        return 0;
    }
}
