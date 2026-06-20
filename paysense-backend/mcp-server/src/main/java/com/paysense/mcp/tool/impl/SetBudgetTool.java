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
 * MCP Tool: Set a monthly budget for a category.
 * Calls Transaction Service POST /api/transactions/budgets
 * This is a WRITE tool — AI can set budgets, not just read.
 */
@Component
@RequiredArgsConstructor
public class SetBudgetTool implements McpTool {

    private static final Logger log = LoggerFactory.getLogger(SetBudgetTool.class);

    @Qualifier("transactionServiceClient")
    private final WebClient transactionServiceClient;

    @Override
    public String getName() {
        return "set_budget";
    }

    @Override
    public String getDescription() {
        return "Set or update a monthly budget limit for a spending category. " +
               "If a budget already exists for that category/month, it will be updated. " +
               "Use this when the user wants to set, change, or create a budget for a category like FOOD, TRANSPORT, etc.";
    }

    @Override
    public Map<String, ToolDefinition.ToolParameter> getParameters() {
        Map<String, ToolDefinition.ToolParameter> params = new LinkedHashMap<>();
        params.put("userId", ToolDefinition.ToolParameter.builder()
                .type("string").description("The user's ID (UUID)").required(true).build());
        params.put("category", ToolDefinition.ToolParameter.builder()
                .type("string").description("Spending category (e.g., FOOD, TRANSPORT, SHOPPING, ENTERTAINMENT, UTILITIES, HEALTHCARE, OTHER)").required(true).build());
        params.put("amount", ToolDefinition.ToolParameter.builder()
                .type("number").description("Budget limit amount in INR").required(true).build());
        params.put("month", ToolDefinition.ToolParameter.builder()
                .type("integer").description("Month (1-12)").required(true).build());
        params.put("year", ToolDefinition.ToolParameter.builder()
                .type("integer").description("Year (e.g., 2026)").required(true).build());
        return params;
    }

    @Override
    public McpToolResponse execute(McpToolCall call, String jwtToken) {
        try {
            Map<String, Object> params = call.getParameters();
            String userId = (String) params.get("userId");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("userId", userId);
            body.put("category", params.get("category"));
            body.put("amount", params.get("amount"));
            body.put("month", ((Number) params.get("month")).intValue());
            body.put("year", ((Number) params.get("year")).intValue());

            log.info("Executing set_budget: userId={}, category={}, amount={}",
                    userId, params.get("category"), params.get("amount"));

            String response = transactionServiceClient.post()
                    .uri("/api/transactions/budgets")
                    .header("Authorization", "Bearer " + jwtToken)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return McpToolResponse.success(response);
        } catch (Exception e) {
            log.error("Failed to set budget", e);
            return McpToolResponse.failure("Failed to set budget: " + e.getMessage());
        }
    }
}
