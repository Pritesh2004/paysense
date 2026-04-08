package com.paysense.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Internal DTO used when Auth Service calls Payment Service to create a new account on registration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequest {

    private UUID userId;
    private String email;
    private String fullName;
}
