package com.paysense.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "vpa_registry")
@EntityListeners(AuditingEntityListener.class)
public class VpaRegistry {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(unique = true, nullable = false, length = 50)
    private String vpa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "is_primary")
    @Builder.Default
    private Boolean isPrimary = true;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
