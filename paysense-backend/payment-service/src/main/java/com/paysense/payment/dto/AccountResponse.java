package com.paysense.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    private UUID id;
    private UUID userId;
    private String accountNumber;
    private String ifscCode;
    private BigDecimal balance;
    private String accountType;
    private String status;
    private LocalDateTime createdAt;

    // Wallet details
    private WalletInfo wallet;
    private List<String> vpas;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalletInfo {
        private UUID id;
        private BigDecimal balance;
        private BigDecimal dailyLimit;
        private BigDecimal todaySpent;
        private BigDecimal remainingDailyLimit;
        private String status;
    }
}
