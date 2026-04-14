package com.paysense.payment.controller;

import com.paysense.payment.dto.AccountResponse;
import com.paysense.payment.dto.AdminTopupRequestDto;
import com.paysense.payment.dto.CreateAccountRequest;
import com.paysense.payment.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments/accounts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AccountController {

    private final AccountService accountService;

    /**
     * Create account + wallet + VPA for a new user.
     * Called internally by Auth Service on registration.
     */
    @PostMapping("/create")
    public ResponseEntity<AccountResponse> createAccount(@RequestBody CreateAccountRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get account details (including wallet and VPAs) for a user.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID userId) {
        return ResponseEntity.ok(accountService.getAccountByUserId(userId));
    }

    /**
     * Admin endpoint: Add balance to a user's account.
     * Used for seeding test data and demo purposes.
     */
    @PostMapping("/admin/topup")
    public ResponseEntity<AccountResponse> adminTopup(@RequestBody AdminTopupRequestDto request) {
        AccountResponse response = accountService.adminTopup(request.getUserId(), request.getAmount());
        return ResponseEntity.ok(response);
    }
}
