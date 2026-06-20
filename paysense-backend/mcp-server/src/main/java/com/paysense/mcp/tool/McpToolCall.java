package com.paysense.mcp.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents a tool invocation request from Claude AI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolCall {
    private String toolName;
    private Map<String, Object> parameters;
    private String toolUseId; // Claude's tool_use block ID for sending tool_result back
}
