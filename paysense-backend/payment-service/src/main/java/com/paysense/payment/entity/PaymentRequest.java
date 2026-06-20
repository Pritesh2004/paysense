package com.paysense.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_requests")
public class PaymentRequest {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "idempotency_key", unique = true, nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "sender_account_id")
    private UUID senderAccountId;

    @Column(name = "receiver_vpa", length = 50)
    private String receiverVpa;

    @Column(name = "receiver_account_no", length = 16)
    private String receiverAccountNo;

    @Column(name = "receiver_ifsc", length = 11)
    private String receiverIfsc;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_type", nullable = false, length = 10)
    private String paymentType;

    @Column(length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "utr_number", length = 22)
    private String utrNumber;

    @Column(length = 255)
    private String description;

    @Column(name = "razorpay_order_id", length = 50)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 50)
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature", length = 255)
    private String razorpaySignature;

    @Column(name = "initiated_at")
    @Builder.Default
    private LocalDateTime initiatedAt = LocalDateTime.now();

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
