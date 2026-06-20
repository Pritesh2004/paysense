package com.paysense.mcp.service;

import com.paysense.mcp.entity.AiConversation;
import com.paysense.mcp.repository.AiConversationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Manages AI conversation persistence.
 * Saves user and assistant messages to mcp.ai_conversations.
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private final AiConversationRepository conversationRepository;

    /**
     * Save a user message.
     */
    public AiConversation saveUserMessage(UUID userId, String content) {
        AiConversation msg = AiConversation.builder()
                .userId(userId)
                .role("USER")
                .content(content)
                .build();
        return conversationRepository.save(msg);
    }

    /**
     * Save an assistant response with optional tool call metadata.
     */
    public AiConversation saveAssistantMessage(UUID userId, String content, Map<String, Object> toolCalls) {
        AiConversation msg = AiConversation.builder()
                .userId(userId)
                .role("ASSISTANT")
                .content(content)
                .toolCalls(toolCalls)
                .build();
        return conversationRepository.save(msg);
    }

    /**
     * Get recent conversation history for context building.
     * Returns last 20 messages in chronological order (oldest first).
     */
    public List<Map<String, String>> getConversationHistory(UUID userId) {
        List<AiConversation> recent = conversationRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
        // Reverse to chronological order
        Collections.reverse(recent);

        return recent.stream()
                .map(msg -> {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("role", msg.getRole().equalsIgnoreCase("USER") ? "user" : "assistant");
                    entry.put("content", msg.getContent());
                    return entry;
                })
                .toList();
    }
}
