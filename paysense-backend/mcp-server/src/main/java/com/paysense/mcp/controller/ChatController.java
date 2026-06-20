package com.paysense.mcp.controller;

import com.paysense.mcp.dto.ChatRequest;
import com.paysense.mcp.dto.ChatResponse;
import com.paysense.mcp.security.JwtUserDetails;
import com.paysense.mcp.service.ClaudeApiService;
import com.paysense.mcp.service.GeminiApiService;
import com.paysense.mcp.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI Chat Controller — entry point for the Angular chat widget.
 * POST /api/ai/chat — accepts user message, invokes Claude with tools, returns AI response.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    // private final ClaudeApiService claudeApiService;
    private final GeminiApiService geminiApiService;
    private final ConversationService conversationService;

    /**
     * Process a chat message through the AI advisor.
     * Saves user + assistant messages to mcp.ai_conversations.
     * Forwards JWT to Transaction Service for data access.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal JwtUserDetails principal) {

        UUID userId = principal.getId();
        log.info("AI chat request from userId={}: {}", userId, request.getMessage());

        // Get JWT from SecurityContext (stored as credentials by JwtAuthenticationFilter)
        String jwtToken = extractJwtToken();

        // Save user message
        conversationService.saveUserMessage(userId, request.getMessage());

        // Get conversation history for context
        List<Map<String, String>> history = conversationService.getConversationHistory(userId);

        // Call Claude API with tools
        // ClaudeApiService.ClaudeResult result = claudeApiService.chat(
        //         request.getMessage(), userId, jwtToken, history);
        ClaudeApiService.ClaudeResult result = geminiApiService.chat(
                request.getMessage(), userId, jwtToken, history);

        // Save assistant response with tool call metadata
        Map<String, Object> toolCallMeta = new LinkedHashMap<>();
        toolCallMeta.put("toolsUsed", result.toolsUsed());
        conversationService.saveAssistantMessage(userId, result.response(), toolCallMeta);

        // Build response
        ChatResponse response = ChatResponse.builder()
                .response(result.response())
                .toolsUsed(result.toolsUsed())
                .conversationId(request.getConversationId() != null
                        ? request.getConversationId() : UUID.randomUUID().toString())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Get conversation history for the authenticated user.
     */
    @GetMapping("/history")
    public ResponseEntity<List<Map<String, String>>> getHistory(
            @AuthenticationPrincipal JwtUserDetails principal) {
        List<Map<String, String>> history = conversationService.getConversationHistory(principal.getId());
        return ResponseEntity.ok(history);
    }

    private String extractJwtToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof String) {
            return (String) auth.getCredentials();
        }
        return "";
    }
}
