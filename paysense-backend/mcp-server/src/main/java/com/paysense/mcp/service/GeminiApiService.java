package com.paysense.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Service
@RequiredArgsConstructor
public class GeminiApiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiApiService.class);
    private static final int MAX_TOOL_ITERATIONS = 5;
    private static final String SYSTEM_PROMPT = """
            You are PaySense AI, a friendly and knowledgeable personal finance advisor integrated into the PaySense fintech platform.
            
            You have access to the user's real financial data through tools. Always use the available tools to fetch actual data before answering questions about spending, budgets, or transactions.
            
            Guidelines:
            - Be concise but helpful. Use bullet points and formatting when appropriate.
            - Always provide specific numbers from the user's actual data, not generic advice.
            - When discussing money, use the ₹ symbol and format numbers clearly.
            - If the user asks about spending, use get_spending_summary first.
            - The userId is automatically provided — use it in all tool calls.
            - Current date context will be provided.
            """;

    // Reusing the same WebClient bean but with a new URL/Key from properties
    @Qualifier("claudeApiClient")
    private final WebClient claudeApiClient;
    private final McpRegistry mcpRegistry;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.model:gemini-1.5-flash}")
    private String model;
    
    @Value("${app.gemini.api-key:mock-key}")
    private String apiKey;

    @Value("${app.gemini.api-url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String apiUrl;

    @CircuitBreaker(name = "geminiApi", fallbackMethod = "chatFallback")
    public ClaudeApiService.ClaudeResult chat(String userMessage, UUID userId, String jwtToken,
                                              List<Map<String, String>> history) {
        try {
            // If the API key is not set properly, throw to trigger fallback mock mode
            if (apiKey == null || apiKey.contains("your-gemini-api-key-here") || apiKey.equals("mock-key")) {
                throw new AiServiceException("401 Unauthorized - No valid Gemini API Key found");
            }

            List<Map<String, Object>> contents = new ArrayList<>();

            // History
            for (Map<String, String> msg : history) {
                Map<String, Object> historyMsg = new LinkedHashMap<>();
                historyMsg.put("role", msg.get("role").equals("assistant") ? "model" : "user");
                historyMsg.put("parts", List.of(Map.of("text", msg.get("content"))));
                contents.add(historyMsg);
            }

            // Current message
            String enrichedMessage = userMessage + "\n\n[Context: userId=" + userId +
                    ", currentDate=" + java.time.LocalDate.now() + "]";
            Map<String, Object> userMsg = new LinkedHashMap<>();
            userMsg.put("role", "user");
            userMsg.put("parts", List.of(Map.of("text", enrichedMessage)));
            contents.add(userMsg);

            List<String> toolsUsed = new ArrayList<>();

            for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
                Map<String, Object> requestBody = new LinkedHashMap<>();
                
                // System Instruction
                requestBody.put("system_instruction", Map.of("parts", Map.of("text", SYSTEM_PROMPT)));
                requestBody.put("contents", contents);
                requestBody.put("tools", mcpRegistry.getGeminiToolDefinitions());

                log.info("Sending request to Gemini (iteration {})", iteration + 1);

                String endpoint = apiUrl + "/" + model + ":generateContent?key=" + apiKey;

                String responseJson = WebClient.create().post()
                        .uri(endpoint)
                        .header("Content-Type", "application/json")
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode responseNode = objectMapper.readTree(responseJson);
                JsonNode candidates = responseNode.path("candidates");
                if (candidates.isEmpty()) break;
                
                JsonNode firstCandidate = candidates.get(0);
                JsonNode contentNode = firstCandidate.path("content");
                JsonNode partsArray = contentNode.path("parts");
                
                boolean hasToolCall = false;
                StringBuilder finalResponse = new StringBuilder();
                List<Map<String, Object>> toolResultsParts = new ArrayList<>();

                for (JsonNode part : partsArray) {
                    if (part.has("functionCall")) {
                        hasToolCall = true;
                        JsonNode functionCall = part.get("functionCall");
                        String toolName = functionCall.path("name").asText();
                        JsonNode argsNode = functionCall.path("args");
                        
                        Map<String, Object> params = objectMapper.convertValue(argsNode, Map.class);
                        log.info("Gemini requested tool: {} with params: {}", toolName, params);
                        toolsUsed.add(toolName);

                        McpToolCall toolCall = McpToolCall.builder()
                                .toolName(toolName)
                                .parameters(params)
                                .build();

                        McpToolResponse toolResponse = executeTool(toolCall, jwtToken);

                        // Build Gemini tool response part
                        Map<String, Object> functionResponse = new LinkedHashMap<>();
                        functionResponse.put("name", toolName);
                        functionResponse.put("response", Map.of("result", toolResponse.isSuccess() ? toolResponse.getData() : toolResponse.getError()));
                        
                        toolResultsParts.add(Map.of("functionResponse", functionResponse));
                    } else if (part.has("text")) {
                        finalResponse.append(part.path("text").asText());
                    }
                }

                if (hasToolCall) {
                    // Add assistant's functionCall to history
                    Map<String, Object> modelMsg = new LinkedHashMap<>();
                    modelMsg.put("role", "model");
                    modelMsg.put("parts", objectMapper.treeToValue(partsArray, List.class));
                    contents.add(modelMsg);

                    // Add our functionResponse to history
                    Map<String, Object> toolResultMsg = new LinkedHashMap<>();
                    toolResultMsg.put("role", "function");
                    toolResultMsg.put("parts", toolResultsParts);
                    contents.add(toolResultMsg);
                } else {
                    return new ClaudeApiService.ClaudeResult(finalResponse.toString(), toolsUsed);
                }
            }

            return new ClaudeApiService.ClaudeResult("I reached my tool execution limit.", toolsUsed);

        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            throw new AiServiceException("Failed to get AI response: " + e.getMessage());
        }
    }

    private McpToolResponse executeTool(McpToolCall call, String jwtToken) {
        Optional<McpTool> tool = mcpRegistry.getTool(call.getToolName());
        if (tool.isEmpty()) {
            return McpToolResponse.failure("Tool not found: " + call.getToolName());
        }
        try {
            return tool.get().execute(call, jwtToken);
        } catch (Exception e) {
            return McpToolResponse.failure("Tool execution failed: " + e.getMessage());
        }
    }

    public ClaudeApiService.ClaudeResult chatFallback(String userMessage, UUID userId, String jwtToken,
                                                      List<Map<String, String>> history, Throwable t) {
        log.warn("Gemini API circuit breaker triggered. Fallback response. Error: {}", t.getMessage());
        
        if (t.getMessage() != null && t.getMessage().contains("401")) {
            try {
                McpToolCall mockCall = McpToolCall.builder()
                        .toolName("get_account_summary")
                        .parameters(Map.of())
                        .build();
                McpToolResponse mockResp = executeTool(mockCall, jwtToken);
                String data = mockResp.isSuccess() ? objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mockResp.getData()) : "Error fetching data.";
                
                return new ClaudeApiService.ClaudeResult(
                        "*(Mock Mode - Gemini)* It looks like you haven't set a Google Gemini API Key, but don't worry! " +
                        "I can still simulate a tool call. I just executed the `get_account_summary` tool on your behalf. " +
                        "Here is your real financial data:\n\n```json\n" + data + "\n```\n\n" +
                        "*(To talk to the real Gemini AI, please provide a valid API key in the configuration.)*",
                        List.of("get_account_summary")
                );
            } catch (Exception e) {
                log.error("Mock fallback failed", e);
            }
        }

        return new ClaudeApiService.ClaudeResult(
                "I'm currently experiencing connectivity issues with my AI backend. Please try again later.\nError: " + t.getMessage(),
                List.of()
        );
    }
}
