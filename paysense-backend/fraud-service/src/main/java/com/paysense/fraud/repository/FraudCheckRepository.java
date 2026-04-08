package com.paysense.fraud.repository;

import com.paysense.fraud.entity.FraudCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface FraudCheckRepository extends JpaRepository<FraudCheck, UUID> {

    List<FraudCheck> findByPaymentRequestId(UUID paymentRequestId);

    List<FraudCheck> findByUserId(UUID userId);

    /**
     * Count recent fraud checks for velocity detection.
     */
    long countByUserIdAndEvaluatedAtAfter(UUID userId, LocalDateTime after);

    List<FraudCheck> findByUserIdAndEvaluatedAtAfterOrderByEvaluatedAtDesc(UUID userId, LocalDateTime after);
}
