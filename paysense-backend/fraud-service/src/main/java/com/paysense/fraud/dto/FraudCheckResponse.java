package com.paysense.fraud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * REST response from Fraud Service → Payment Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckResponse {

    private String decision;    // APPROVED, FLAGGED, BLOCKED
    private int riskScore;      // 0-100
    private List<String> rulesTriggered;
}
