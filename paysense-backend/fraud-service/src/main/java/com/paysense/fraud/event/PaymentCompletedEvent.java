package com.paysense.fraud.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Shared event — consumed from Kafka topic 'payment.completed'.
 * Payment Service publishes as PaymentEvent (fields: paymentRequestId, senderUserId, receiverAccountId).
 * @JsonAlias maps both naming conventions so deserialization always succeeds.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {

    @JsonAlias("paymentRequestId")
    private UUID paymentId;

    @JsonAlias("senderUserId")
    private UUID senderId;

    @JsonAlias("receiverAccountId")
    private UUID receiverId;

    private BigDecimal amount;
    private String paymentType;
    private String utrNumber;
    private String status;
    private LocalDateTime timestamp;
}
