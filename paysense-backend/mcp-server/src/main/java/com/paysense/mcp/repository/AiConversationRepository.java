package com.paysense.mcp.repository;

import com.paysense.mcp.entity.AiConversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

    /**
     * Get conversation history for a user, ordered by creation time (most recent first).
     */
    List<AiConversation> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Get recent conversation history for a user (ascending for context building).
     */
    List<AiConversation> findByUserIdOrderByCreatedAtAsc(UUID userId);

    /**
     * Get the last N messages for context (most recent first, then reverse in service).
     */
    List<AiConversation> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);
}
