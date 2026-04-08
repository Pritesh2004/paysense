package com.paysense.payment.scheduler;

import com.paysense.payment.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wallet Daily Reset Scheduler.
 *
 * Runs at midnight to reset wallet today_spent to 0 for all wallets
 * whose last_reset_date is before today.
 */
@Component
@RequiredArgsConstructor
public class WalletResetScheduler {

    private static final Logger log = LoggerFactory.getLogger(WalletResetScheduler.class);

    private final WalletRepository walletRepository;

    /**
     * Runs at midnight every day to reset daily spending limits.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void resetDailySpends() {
        int resetCount = walletRepository.resetDailySpends();
        log.info("Midnight wallet reset: {} wallets reset to ₹0 daily spend", resetCount);
    }
}
