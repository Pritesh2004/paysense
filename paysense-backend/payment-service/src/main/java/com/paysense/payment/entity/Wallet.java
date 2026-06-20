package com.paysense.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", unique = true, nullable = false)
    private UUID userId;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "daily_limit", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal dailyLimit = new BigDecimal("10000.00");

    @Column(name = "today_spent", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal todaySpent = BigDecimal.ZERO;

    @Column(length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Version
    @Builder.Default
    private Long version = 0L;

    @Column(name = "last_reset_date")
    @Builder.Default
    private LocalDate lastResetDate = LocalDate.now();
}
