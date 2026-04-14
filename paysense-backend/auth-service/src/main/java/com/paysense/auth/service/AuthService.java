package com.paysense.auth.service;

import com.paysense.auth.config.JwtProperties;
import com.paysense.auth.dto.LoginRequest;
import com.paysense.auth.dto.RegisterRequest;
import com.paysense.auth.dto.TokenResponse;
import com.paysense.auth.entity.RefreshToken;
import com.paysense.auth.entity.User;
import com.paysense.auth.entity.Role;
import com.paysense.auth.repository.RefreshTokenRepository;
import com.paysense.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final JwtProperties jwtProperties;

    @Value("${app.payment-service.url:http://localhost:8082}")
    private String paymentServiceUrl;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new RuntimeException("Phone already exists");
        }

        var user = User.builder()
                

.email(request.getEmail())
 .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(Role.USER)
                .isActive(true)
                .isVerified(false)
                .build();
        
        var savedUser = userRepository.save(user);

        // Here you would make a REST call to payment-service to create account
        createAccountInPaymentService(savedUser);

        var jwtToken = jwtService.generateAccessToken(savedUser);
        var refreshToken = jwtService.generateRefreshToken();
        
        saveRefreshToken(savedUser, refreshToken, "Unknown");

        return TokenResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken)
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .build();
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        var jwtToken = jwtService.generateAccessToken(user);
        var refreshToken = jwtService.generateRefreshToken();
        
        saveRefreshToken(user, refreshToken, "Unknown");

        return TokenResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .build();
    }

    @Transactional
    public TokenResponse refreshToken(String token) {
        String tokenHash = hashToken(token);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));

        if (storedToken.getIsRevoked() || storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Refresh token expired or revoked");
        }

        var user = storedToken.getUser();
        var jwtToken = jwtService.generateAccessToken(user);
        
        return TokenResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(token)
                .userId(user.getId())
                .email(user.getEmail())
                .build();
    }

    @Transactional
    public void logout(String token) {
        String tokenHash = hashToken(token);
        Optional<RefreshToken> storedToken = refreshTokenRepository.findByTokenHash(tokenHash);
        storedToken.ifPresent(rt -> {
            rt.setIsRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    private void saveRefreshToken(User user, String token, String deviceInfo) {
        var rToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(token))
                .deviceInfo(deviceInfo)
                .ipAddress("")
                .expiresAt(LocalDateTime.now().plus(jwtProperties.getRefreshToken().getExpiration(), java.time.temporal.ChronoUnit.MILLIS))
                .isRevoked(false)
                .build();
        refreshTokenRepository.save(rToken);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Could not hash token", e);
        }
    }

    private void createAccountInPaymentService(User user) {
        LoggerFactory.getLogger(AuthService.class).info("Calling payment service to create account for user: " + user.getId());
        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            String url = paymentServiceUrl + "/api/payments/accounts/create";

            java.util.Map<String, String> request = new java.util.HashMap<>();
            request.put("userId", user.getId().toString());
            request.put("email", user.getEmail());
            request.put("fullName", user.getFullName());

            restTemplate.postForObject(url, request, Object.class);
            LoggerFactory.getLogger(AuthService.class).info("Successfully created payment account for user: " + user.getId());
        } catch (Exception e) {
            LoggerFactory.getLogger(AuthService.class).error("Failed to create payment account for user: " + user.getId(), e);
            // In a production resilient system, this should push to a dead letter queue or retry.
        }
    }
}
