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
 * MCP Tool: Get spending summary with category breakdown.
 * Calls Transaction Service GET /api/transactions/spending-summary
 */
@Component
@RequiredArgsConstructor
public class GetSpendingSummaryTool implements McpTool {

    private static final Logger log = LoggerFactory.getLogger(GetSpendingSummaryTool.class);

    @Qualifier("transactionServiceClient")
    private final WebClient transactionServiceClient;

    @Override
    public String getName() {
        return "get_spending_summary";
    }

    @Override
    public String getDescription() {
        return "Get the user's spending summary for a given month and year. " +
               "Returns total amount spent and breakdown by category (e.g., FOOD, TRANSPORT, SHOPPING). " +
               "Use this when the user asks about their spending, expenses, or how much they spent.";
    }

    @Override
    public Map<String, ToolDefinition.ToolParameter> getParameters() {
        Map<String, ToolDefinition.ToolParameter> params = new LinkedHashMap<>();
        params.put("userId", ToolDefinition.ToolParameter.builder()
                .type("string").description("The user's ID (UUID)").required(true).build());
        params.put("month", ToolDefinition.ToolParameter.builder()
                .type("integer").description("Month (1-12)").required(true).build());
        params.put("year", ToolDefinition.ToolParameter.builder()
                .type("integer").description("Year (e.g., 2026)").required(true).build());
        return params;
    }

    @Override
    public McpToolResponse execute(McpToolCall call, String jwtToken) {
        try {
            String userId = (String) call.getParameters().get("userId");
            int month = ((Number) call.getParameters().get("month")).intValue();
            int year = ((Number) call.getParameters().get("year")).intValue();

            log.info("Executing get_spending_summary: userId={}, month={}, year={}", userId, month, year);

            String response = transactionServiceClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/transactions/spending-summary")
                            .queryParam("userId", userId)
                            .queryParam("month", month)
                            .queryParam("year", year)
                            .build())
                    .header("Authorization", "Bearer " + jwtToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return McpToolResponse.success(response);
        } catch (Exception e) {
            log.error("Failed to get spending summary", e);
            return McpToolResponse.failure("Failed to retrieve spending summary: " + e.getMessage());
        }
    }
}
