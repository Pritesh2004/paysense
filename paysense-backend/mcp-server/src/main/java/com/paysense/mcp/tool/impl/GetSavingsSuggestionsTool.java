package com.paysense.mcp.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * MCP Tool: Get savings suggestions — analyzes spending and recommends top 3 categories to save.
 * Aggregates spending data from the spending summary and compares to averages.
 */
@Component
@RequiredArgsConstructor
public class GetSavingsSuggestionsTool implements McpTool {

    private static final Logger log = LoggerFactory.getLogger(GetSavingsSuggestionsTool.class);

    @Qualifier("transactionServiceClient")
    private final WebClient transactionServiceClient;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "get_savings_suggestions";
    }

    @Override
    public String getDescription() {
        return "Analyze the user's spending patterns and suggest top 3 categories where they can save money. " +
               "Compares current month spending to previous months and identifies areas of overspending. " +
               "Use this when the user asks for savings tips, how to reduce expenses, or financial advice.";
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

            log.info("Executing get_savings_suggestions: userId={}, month={}, year={}", userId, month, year);

            // Get current month spending
            String currentSpendingJson = transactionServiceClient.get()
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

            // Get previous month spending for comparison
            int prevMonth = month == 1 ? 12 : month - 1;
            int prevYear = month == 1 ? year - 1 : year;

            String prevSpendingJson = transactionServiceClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/transactions/spending-summary")
                            .queryParam("userId", userId)
                            .queryParam("month", prevMonth)
                            .queryParam("year", prevYear)
                            .build())
                    .header("Authorization", "Bearer " + jwtToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorReturn("{\"totalSpent\": 0, \"breakdown\": []}")
                    .block();

            // Parse and analyze
            JsonNode currentData = objectMapper.readTree(currentSpendingJson);
            JsonNode prevData = objectMapper.readTree(prevSpendingJson);

            Map<String, BigDecimal> prevSpending = new HashMap<>();
            if (prevData.has("breakdown")) {
                for (JsonNode cat : prevData.get("breakdown")) {
                    prevSpending.put(cat.get("category").asText(),
                            new BigDecimal(cat.get("amount").asText()));
                }
            }

            List<Map<String, Object>> suggestions = new ArrayList<>();
            if (currentData.has("breakdown")) {
                for (JsonNode cat : currentData.get("breakdown")) {
                    String category = cat.get("category").asText();
                    BigDecimal currentAmount = new BigDecimal(cat.get("amount").asText());
                    BigDecimal prevAmount = prevSpending.getOrDefault(category, BigDecimal.ZERO);

                    BigDecimal increase = prevAmount.compareTo(BigDecimal.ZERO) > 0
                            ? currentAmount.subtract(prevAmount)
                                    .divide(prevAmount, 2, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;

                    // Estimate potential savings (reduce to previous month level or by 20%)
                    BigDecimal potentialSavings = prevAmount.compareTo(BigDecimal.ZERO) > 0
                            ? currentAmount.subtract(prevAmount).max(BigDecimal.ZERO)
                            : currentAmount.multiply(BigDecimal.valueOf(0.20)).setScale(2, RoundingMode.HALF_UP);

                    Map<String, Object> suggestion = new LinkedHashMap<>();
                    suggestion.put("category", category);
                    suggestion.put("currentSpend", currentAmount);
                    suggestion.put("previousSpend", prevAmount);
                    suggestion.put("increasePercentage", increase);
                    suggestion.put("potentialSavings", potentialSavings);
                    suggestion.put("tip", generateTip(category, increase));
                    suggestions.add(suggestion);
                }
            }

            // Sort by potential savings descending, take top 3
            suggestions.sort((a, b) -> ((BigDecimal) b.get("potentialSavings"))
                    .compareTo((BigDecimal) a.get("potentialSavings")));

            List<Map<String, Object>> top3 = suggestions.stream().limit(3).toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("month", month);
            result.put("year", year);
            result.put("suggestions", top3);
            result.put("totalPotentialSavings", top3.stream()
                    .map(s -> (BigDecimal) s.get("potentialSavings"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

            return McpToolResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to get savings suggestions", e);
            return McpToolResponse.failure("Failed to generate savings suggestions: " + e.getMessage());
        }
    }

    private String generateTip(String category, BigDecimal increasePercentage) {
        return switch (category.toUpperCase()) {
            case "FOOD" -> "Consider meal prepping or cooking at home more often to reduce food spending.";
            case "TRANSPORT" -> "Try carpooling, public transport, or cycling for short distances.";
            case "SHOPPING" -> "Create a wishlist and wait 48 hours before making non-essential purchases.";
            case "ENTERTAINMENT" -> "Look for free events, use streaming subscriptions wisely, or set a fun budget.";
            case "UTILITIES" -> "Monitor electricity usage, switch to energy-efficient appliances.";
            case "HEALTHCARE" -> "Consider generic medications and preventive health checkups.";
            default -> increasePercentage.compareTo(BigDecimal.valueOf(20)) > 0
                    ? "Your spending in this category increased significantly. Review recent transactions."
                    : "Track your expenses more closely in this category to identify savings opportunities.";
        };
    }
}
