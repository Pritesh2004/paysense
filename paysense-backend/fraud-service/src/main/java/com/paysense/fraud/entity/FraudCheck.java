package com.paysense.fraud.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fraud_checks")
public class FraudCheck {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "payment_request_id", nullable = false)
    private UUID paymentRequestId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "risk_score")
    @Builder.Default
    private Integer riskScore = 0;

    @Column(length = 20)
    private String decision;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "rules_triggered", columnDefinition = "text[]")
    private String[] rulesTriggered;

    @Column(name = "evaluated_at")
    @Builder.Default
    private LocalDateTime evaluatedAt = LocalDateTime.now();
}
