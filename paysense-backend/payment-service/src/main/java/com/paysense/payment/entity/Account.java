package com.paysense.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "accounts")
@EntityListeners(AuditingEntityListener.class)
public class Account {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", unique = true, nullable = false)
    private UUID userId;

    @Column(name = "account_number", unique = true, nullable = false, length = 16)
    private String accountNumber;

    @Column(name = "ifsc_code", length = 11)
    @Builder.Default
    private String ifscCode = "PAYS0000001";

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "account_type", length = 20)
    @Builder.Default
    private String accountType = "SAVINGS";

    @Column(length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Version
    @Builder.Default
    private Long version = 0L;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
