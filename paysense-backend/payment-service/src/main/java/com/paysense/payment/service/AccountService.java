package com.paysense.payment.service;

import com.paysense.payment.dto.AccountResponse;
import com.paysense.payment.dto.CreateAccountRequest;
import com.paysense.payment.entity.Account;
import com.paysense.payment.entity.VpaRegistry;
import com.paysense.payment.entity.Wallet;
import com.paysense.payment.exception.AccountNotFoundException;
import com.paysense.payment.exception.PaymentException;
import com.paysense.payment.repository.AccountRepository;
import com.paysense.payment.repository.VpaRegistryRepository;
import com.paysense.payment.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final VpaRegistryRepository vpaRegistryRepository;

    /**
     * Counter for generating unique account numbers.
     * In production, this would use a DB sequence.
     */
    private static final AtomicLong ACCOUNT_SEQ = new AtomicLong(1000);

    @jakarta.annotation.PostConstruct
    public void seedInitialMoney() {
        log.info("Seeding test money to all existing accounts...");
        accountRepository.findAll().forEach(acc -> {
            if (acc.getBalance().compareTo(new BigDecimal("10000")) < 0) {
                acc.setBalance(new BigDecimal("10000.00"));
                accountRepository.save(acc);
            }
        });
        walletRepository.findAll().forEach(w -> {
            if (w.getBalance().compareTo(new BigDecimal("5000")) < 0) {
                w.setBalance(new BigDecimal("5000.00"));
                walletRepository.save(w);
            }
        });
    }

    /**
     * Creates a new account + wallet + default VPA for a user.
     * Called by Auth Service on user registration.
     */
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        if (accountRepository.existsByUserId(request.getUserId())) {
            throw new PaymentException("Account already exists for user: " + request.getUserId());
        }

        // 1. Create Account
        String accountNumber = generateAccountNumber();
        Account account = Account.builder()
                .userId(request.getUserId())
                .accountNumber(accountNumber)
                .ifscCode("PAYS0000001")
                .balance(new BigDecimal("10000.00"))
                .accountType("SAVINGS")
                .status("ACTIVE")
                .build();
        account = accountRepository.save(account);
        log.info("Created payment account: {} for user: {}", accountNumber, request.getUserId());

        // 2. Create Wallet
        Wallet wallet = Wallet.builder()
                .userId(request.getUserId())
                .balance(new BigDecimal("5000.00"))
                .dailyLimit(new BigDecimal("10000.00"))
                .todaySpent(BigDecimal.ZERO)
                .status("ACTIVE")
                .build();
        walletRepository.save(wallet);
        log.info("Created wallet for user: {}", request.getUserId());

        // 3. Create default VPA (email prefix @paysense)
        String vpaHandle = extractVpaHandle(request.getEmail());
        String vpa = vpaHandle + "@paysense";

        // If VPA already exists, add a random suffix
        if (vpaRegistryRepository.existsByVpa(vpa)) {
            vpa = vpaHandle + (int)(Math.random() * 1000) + "@paysense";
        }

        VpaRegistry vpaEntry = VpaRegistry.builder()
                .vpa(vpa)
                .account(account)
                .isPrimary(true)
                .isActive(true)
                .build();
        vpaRegistryRepository.save(vpaEntry);
        log.info("Created VPA: {} for account: {}", vpa, accountNumber);

        return buildAccountResponse(account, wallet, List.of(vpa));
    }

    /**
     * Get account details for a user.
     */
    public AccountResponse getAccountByUserId(UUID userId) {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found for user: " + userId));

        Wallet wallet = walletRepository.findByUserId(userId).orElse(null);

        List<String> vpas = vpaRegistryRepository.findByAccountIdAndIsActiveTrue(account.getId())
                .stream()
                .map(VpaRegistry::getVpa)
                .collect(Collectors.toList());

        return buildAccountResponse(account, wallet, vpas);
    }

    /**
     * Admin top-up: add money to a user's account balance.
     */
    @Transactional
    public AccountResponse adminTopup(UUID userId, BigDecimal amount) {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found for user: " + userId));

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        log.info("Admin top-up: ₹{} added to user={}, new balance=₹{}", amount, userId, account.getBalance());

        Wallet wallet = walletRepository.findByUserId(userId).orElse(null);
        List<String> vpas = vpaRegistryRepository.findByAccountIdAndIsActiveTrue(account.getId())
                .stream().map(VpaRegistry::getVpa).collect(Collectors.toList());

        return buildAccountResponse(account, wallet, vpas);
    }

    // ── Helpers ──────────────────────────────────────────────

    private String generateAccountNumber() {
        long seq = ACCOUNT_SEQ.incrementAndGet();
        return String.format("PS%010d", seq);
    }

    private String extractVpaHandle(String email) {
        if (email == null || !email.contains("@")) {
            return "user" + System.currentTimeMillis();
        }
        return email.substring(0, email.indexOf("@")).replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    private AccountResponse buildAccountResponse(Account account, Wallet wallet, List<String> vpas) {
        AccountResponse.WalletInfo walletInfo = null;
        if (wallet != null) {
            BigDecimal remaining = wallet.getDailyLimit().subtract(wallet.getTodaySpent());
            walletInfo = AccountResponse.WalletInfo.builder()
                    .id(wallet.getId())
                    .balance(wallet.getBalance())
                    .dailyLimit(wallet.getDailyLimit())
                    .todaySpent(wallet.getTodaySpent())
                    .remainingDailyLimit(remaining.max(BigDecimal.ZERO))
                    .status(wallet.getStatus())
                    .build();
        }

        return AccountResponse.builder()
                .id(account.getId())
                .userId(account.getUserId())
                .accountNumber(account.getAccountNumber())
                .ifscCode(account.getIfscCode())
                .balance(account.getBalance())
                .accountType(account.getAccountType())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .wallet(walletInfo)
                .vpas(vpas)
                .build();
    }
}
