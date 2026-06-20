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

    @org.springframework.data.jpa.repository.Query("SELECT p FROM PaymentRequest p WHERE " +
            "p.senderAccountId = :accountId " +
            "OR (p.status = 'SUCCESS' AND (p.receiverAccountNo = :accountNumber " +
            "  OR (COALESCE(:vpas, NULL) IS NOT NULL AND p.receiverVpa IN :vpas))) " +
            "ORDER BY p.initiatedAt DESC")
    org.springframework.data.domain.Page<PaymentRequest> findPaymentHistory(
            @org.springframework.data.repository.query.Param("accountId") UUID accountId,
            @org.springframework.data.repository.query.Param("accountNumber") String accountNumber,
            @org.springframework.data.repository.query.Param("vpas") List<String> vpas,
            org.springframework.data.domain.Pageable pageable
    );

    /**
     * Find all PENDING NEFT payments for batch settlement.
     */
    List<PaymentRequest> findByPaymentTypeAndStatus(String paymentType, String status);

    /**
     * Find recent payments by sender for fraud frequency check.
     */
    List<PaymentRequest> findBySenderAccountIdAndStatusOrderByInitiatedAtDesc(UUID senderAccountId, String status);

    Optional<PaymentRequest> findByRazorpayOrderId(String razorpayOrderId);
}
