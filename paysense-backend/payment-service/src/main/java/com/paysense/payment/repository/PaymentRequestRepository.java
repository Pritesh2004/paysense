package com.paysense.payment.repository;

import com.paysense.payment.entity.PaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, UUID> {

    Optional<PaymentRequest> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<PaymentRequest> findBySenderAccountId(UUID senderAccountId);

    /**
     * Find all PENDING NEFT payments for batch settlement.
     */
    List<PaymentRequest> findByPaymentTypeAndStatus(String paymentType, String status);

    /**
     * Find recent payments by sender for fraud frequency check.
     */
    List<PaymentRequest> findBySenderAccountIdAndStatusOrderByInitiatedAtDesc(UUID senderAccountId, String status);
}
