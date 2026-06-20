package com.paysense.fraud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blacklisted_vpas")
public class BlacklistedVpa {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(unique = true, nullable = false, length = 50)
    private String vpa;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "added_at")
    @Builder.Default
    private LocalDateTime addedAt = LocalDateTime.now();
}
