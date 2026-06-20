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
 * MCP Tool: Get recent transactions, optionally filtered by category.
 * Calls Transaction Service GET /api/transactions/recent
 */
@Component
@RequiredArgsConstructor
public class GetRecentTransactionsTool implements McpTool {

    private static final Logger log = LoggerFactory.getLogger(GetRecentTransactionsTool.class);

    @Qualifier("transactionServiceClient")
    private final WebClient transactionServiceClient;

    @Override
    public String getName() {
        return "get_recent_transactions";
    }

    @Override
    public String getDescription() {
        return "Get the user's recent transactions. Can optionally filter by category. " +
               "Returns a list of transactions with amount, type (DEBIT/CREDIT), category, and date. " +
               "Use this when the user asks to see their recent transactions, last payments, or transaction history.";
    }

    @Override
    public Map<String, ToolDefinition.ToolParameter> getParameters() {
        Map<String, ToolDefinition.ToolParameter> params = new LinkedHashMap<>();
        params.put("userId", ToolDefinition.ToolParameter.builder()
                .type("string").description("The user's ID (UUID)").required(true).build());
        params.put("limit", ToolDefinition.ToolParameter.builder()
                .type("integer").description("Number of transactions to return (default 10, max 50)").required(false).build());
        params.put("category", ToolDefinition.ToolParameter.builder()
                .type("string").description("Filter by category (e.g., FOOD, TRANSPORT, SHOPPING). Optional.").required(false).build());
        return params;
    }

    @Override
    public McpToolResponse execute(McpToolCall call, String jwtToken) {
        try {
            String userId = (String) call.getParameters().get("userId");
            int limit = call.getParameters().containsKey("limit")
                    ? ((Number) call.getParameters().get("limit")).intValue() : 10;
            String category = (String) call.getParameters().get("category");

            log.info("Executing get_recent_transactions: userId={}, limit={}, category={}", userId, limit, category);

            String response = transactionServiceClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/api/transactions/recent")
                                .queryParam("userId", userId)
                                .queryParam("limit", limit);
                        if (category != null && !category.isBlank()) {
                            builder.queryParam("category", category);
                        }
                        return builder.build();
                    })
                    .header("Authorization", "Bearer " + jwtToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return McpToolResponse.success(response);
        } catch (Exception e) {
            log.error("Failed to get recent transactions", e);
            return McpToolResponse.failure("Failed to retrieve recent transactions: " + e.getMessage());
        }
    }
}
