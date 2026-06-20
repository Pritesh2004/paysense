package com.paysense.mcp.tool.impl;

import com.paysense.mcp.dto.ToolDefinition;
import com.paysense.mcp.tool.McpTool;
import com.paysense.mcp.tool.McpToolCall;
import com.paysense.mcp.tool.McpToolResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP Tool: Detect unusual spending patterns.
 * Calls Transaction Service GET /api/transactions/unusual
 */
@Component
@RequiredArgsConstructor
public class DetectUnusualSpendingTool implements McpTool {

    private static final Logger log = LoggerFactory.getLogger(DetectUnusualSpendingTool.class);

    @Qualifier("transactionServiceClient")
    private final WebClient transactionServiceClient;

    @Override
    public String getName() {
        return "detect_unusual_spending";
    }

    @Override
    public String getDescription() {
        return "Detect unusual or anomalous spending patterns for the user. " +
               "Identifies categories where current month spending is 2x or more above the user's 3-month average. " +
               "Use this when the user asks about unusual charges, spending alerts, or wants a financial health check.";
    }

    @Override
    public Map<String, ToolDefinition.ToolParameter> getParameters() {
        Map<String, ToolDefinition.ToolParameter> params = new LinkedHashMap<>();
        params.put("userId", ToolDefinition.ToolParameter.builder()
                .type("string").description("The user's ID (UUID)").required(true).build());
        return params;
    }

    @Override
    public McpToolResponse execute(McpToolCall call, String jwtToken) {
        try {
            String userId = (String) call.getParameters().get("userId");

            log.info("Executing detect_unusual_spending: userId={}", userId);

            String response = transactionServiceClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/transactions/unusual")
                            .queryParam("userId", userId)
                            .build())
                    .header("Authorization", "Bearer " + jwtToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return McpToolResponse.success(response);
        } catch (Exception e) {
            log.error("Failed to detect unusual spending", e);
            return McpToolResponse.failure("Failed to detect unusual spending: " + e.getMessage());
        }
    }
}
