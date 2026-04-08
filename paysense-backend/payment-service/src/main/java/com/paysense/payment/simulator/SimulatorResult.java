package com.paysense.payment.simulator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result from UPI / NEFT simulators.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulatorResult {

    private boolean success;
    private String utrNumber;
    private String failureReason;
    private long latencyMs;

    public static SimulatorResult ok(String utrNumber, long latencyMs) {
        return SimulatorResult.builder()
                .success(true)
                .utrNumber(utrNumber)
                .latencyMs(latencyMs)
                .build();
    }

    public static SimulatorResult fail(String reason, long latencyMs) {
        return SimulatorResult.builder()
                .success(false)
                .failureReason(reason)
                .latencyMs(latencyMs)
                .build();
    }
}
