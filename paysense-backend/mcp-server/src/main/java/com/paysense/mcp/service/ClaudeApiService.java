package com.paysense.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paysense.mcp.exception.AiServiceException;
import com.paysense.mcp.registry.McpRegistry;
import com.paysense.mcp.tool.McpTool;
import com.paysense.mcp.tool.McpToolCall;
import com.paysense.mcp.tool.McpToolResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * Integrates with Anthropic Claude API for AI-powered financial advice.
 *
 * Flow:
 * 1. Send user message + tool definitions to Claude
 * 2. If Claude responds with tool_use blocks, execute those tools via McpRegistry
 * 3. Send tool_result back to Claude
 * 4. Repeat until Claude gives a final text response (max 5 iterations)
 * 5. Return the final response
 *
 * Protected by Resilience4j circuit breaker for API resilience.
 */
// @Service
@RequiredArgsConstructor
public class ClaudeApiService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeApiService.class);
    private static final int MAX_TOOL_ITERATIONS = 5;
    private static final String SYSTEM_PROMPT = """
            You are PaySense AI, a friendly and knowledgeable personal finance advisor integrated into the PaySense fintech platform.
            
            You have access to the user's real financial data through tools. Always use the available tools to fetch actual data before answering questions about spending, budgets, or transactions.
            
            Guidelines:
            - Be concise but helpful. Use bullet points and formatting when appropriate.
            - Always provide specific numbers from the user's actual data, not generic advice.
            - When discussing money, use the ₹ symbol and format numbers clearly.
            - If the user asks about spending, use get_spending_summary first.
            - If the user asks about budgets, use get_budget_status first.
            - If the user wants to set a budget, use set_budget.
            - If the user asks about unusual charges, use detect_unusual_spending.
            - If the user wants savings tips, use get_savings_suggestions.
            - For recent transactions, use get_recent_transactions.
            - The userId is automatically provided — use it in all tool calls.
            - Current date context will be provided.
            """;

    @Qualifier("claudeApiClient")
    private final WebClient claudeApiClient;
    private final McpRegistry mcpRegistry;
    private final ObjectMapper objectMapper;

    @Value("${app.claude.model}")
    private String model;

    @Value("${app.claude.max-tokens}")
    private int maxTokens;

    /**
     * Process a user message through Claude with tool calling.
     *
     * @param userMessage The user's chat message
     * @param userId      The authenticated user's ID (injected into tool calls)
     * @param jwtToken    Raw JWT for forwarding to Transaction Service
     * @param history     Previous conversation messages for context
     * @return Claude's final text response + list of tools used
     */
    @CircuitBreaker(name = "claudeApi", fallbackMethod = "chatFallback")
    public ClaudeResult chat(String userMessage, UUID userId, String jwtToken,
                              List<Map<String, String>> history) {
        try {
            // Build initial messages array
            List<Map<String, Object>> messages = new ArrayList<>();

            // Add conversation history
            for (Map<String, String> msg : history) {
                Map<String, Object> historyMsg = new LinkedHashMap<>();
                historyMsg.put("role", msg.get("role"));
                historyMsg.put("content", msg.get("content"));
                messages.add(historyMsg);
            }

            // Add current user message with userId context
            String enrichedMessage = userMessage + "\n\n[Context: userId=" + userId +
                    ", currentDate=" + java.time.LocalDate.now() +
                    ", currentMonth=" + java.time.LocalDate.now().getMonthValue() +
                    ", currentYear=" + java.time.LocalDate.now().getYear() + "]";

            Map<String, Object> userMsg = new LinkedHashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", enrichedMessage);
            messages.add(userMsg);

            List<String> toolsUsed = new ArrayList<>();

            // Tool calling loop
            for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
                // Build request
                Map<String, Object> requestBody = new LinkedHashMap<>();
                requestBody.put("model", model);
                requestBody.put("max_tokens", maxTokens);
                requestBody.put("system", SYSTEM_PROMPT);
                requestBody.put("messages", messages);
                requestBody.put("tools", mcpRegistry.getClaudeToolDefinitions());

                log.info("Sending request to Claude (iteration {})", iteration + 1);

                // Call Claude API
                String responseJson = claudeApiClient.post()
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode responseNode = objectMapper.readTree(responseJson);
                String stopReason = responseNode.path("stop_reason").asText();
                JsonNode contentArray = responseNode.path("content");

                // Check if Claude wants to use tools
                if ("tool_use".equals(stopReason)) {
                    // Add assistant's response (with tool_use blocks) to messages
                    Map<String, Object> assistantMsg = new LinkedHashMap<>();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.put("content", objectMapper.treeToValue(contentArray, List.class));
                    messages.add(assistantMsg);

                    // Execute each tool_use block
                    List<Map<String, Object>> toolResults = new ArrayList<>();
                    for (JsonNode block : contentArray) {
                        if ("tool_use".equals(block.path("type").asText())) {
                            String toolName = block.path("name").asText();
                            String toolUseId = block.path("id").asText();
                            JsonNode inputNode = block.path("input");

                            Map<String, Object> params = objectMapper.convertValue(inputNode, Map.class);

                            log.info("Claude requested tool: {} with params: {}", toolName, params);
                            toolsUsed.add(toolName);

                            // Execute the tool
                            McpToolCall toolCall = McpToolCall.builder()
                                    .toolName(toolName)
                                    .parameters(params)
                                    .toolUseId(toolUseId)
                                    .build();

                            McpToolResponse toolResponse = executeTool(toolCall, jwtToken);

                            // Build tool_result
                            Map<String, Object> toolResult = new LinkedHashMap<>();
                            toolResult.put("type", "tool_result");
                            toolResult.put("tool_use_id", toolUseId);
                            toolResult.put("content", objectMapper.writeValueAsString(
                                    toolResponse.isSuccess() ? toolResponse.getData() : toolResponse.getError()));
                            toolResults.add(toolResult);
                        }
                    }

                    // Add tool results as user message
                    Map<String, Object> toolResultMsg = new LinkedHashMap<>();
                    toolResultMsg.put("role", "user");
                    toolResultMsg.put("content", toolResults);
                    messages.add(toolResultMsg);

                } else {
                    // "end_turn" — Claude gave final text response
                    StringBuilder finalResponse = new StringBuilder();
                    for (JsonNode block : contentArray) {
                        if ("text".equals(block.path("type").asText())) {
                            finalResponse.append(block.path("text").asText());
                        }
                    }

                    log.info("Claude responded with final answer. Tools used: {}", toolsUsed);
                    return new ClaudeResult(finalResponse.toString(), toolsUsed);
                }
            }

            // Max iterations reached — extract any text from last response
            return new ClaudeResult(
                    "I've gathered your financial data but reached the processing limit. " +
                    "Please try a more specific question.",
                    toolsUsed);

        } catch (Exception e) {
            log.error("Error calling Claude API", e);
            throw new AiServiceException("Failed to get AI response: " + e.getMessage());
        }
    }

    private McpToolResponse executeTool(McpToolCall call, String jwtToken) {
        Optional<McpTool> tool = mcpRegistry.getTool(call.getToolName());
        if (tool.isEmpty()) {
            log.warn("Tool not found: {}", call.getToolName());
            return McpToolResponse.failure("Tool not found: " + call.getToolName());
        }
        try {
            return tool.get().execute(call, jwtToken);
        } catch (Exception e) {
            log.error("Tool execution failed: {}", call.getToolName(), e);
            return McpToolResponse.failure("Tool execution failed: " + e.getMessage());
        }
    }

    public ClaudeResult chatFallback(String userMessage, UUID userId, String jwtToken,
                                      List<Map<String, String>> history, Throwable t) {
        log.warn("Claude API circuit breaker triggered. Fallback response. Error: {}", t.getMessage());
        
        if (t.getMessage() != null && t.getMessage().contains("401")) {
            try {
                McpToolCall mockCall = McpToolCall.builder()
                        .toolName("get_account_summary")
                        .parameters(Map.of())
                        .toolUseId("mock_tool_1")
                        .build();
                McpToolResponse mockResp = executeTool(mockCall, jwtToken);
                String data = mockResp.isSuccess() ? objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mockResp.getData()) : "Error fetching data.";
                
                return new ClaudeResult(
                        "*(Mock Mode)* It looks like you haven't set an Anthropic API Key, but don't worry! " +
                        "I can still simulate a tool call. I just executed the `get_account_summary` tool on your behalf. " +
                        "Here is your real financial data:\n\n```json\n" + data + "\n```\n\n" +
                        "*(To talk to the real AI, please provide a valid API key in the configuration.)*",
                        List.of("get_account_summary")
                );
            } catch (Exception e) {
                log.error("Mock fallback failed", e);
            }
        }

        return new ClaudeResult(
                "I'm currently experiencing connectivity issues with my AI backend. " +
                "Please try again in a few moments. If the issue persists, you can view your " +
                "financial data directly in the dashboard.\n\n" +
                "Error: " + t.getMessage(),
                List.of()
        );
    }

    /**
     * Result from Claude API call.
     */
    public record ClaudeResult(String response, List<String> toolsUsed) {}
}
