package com.paysense.mcp.tool;

import com.paysense.mcp.dto.ToolDefinition;

import java.util.Map;

/**
 * Interface for MCP tools that Claude AI can invoke.
 * Each tool is a callable financial operation backed by the Transaction Service.
 */
public interface McpTool {

    /** Unique tool name (e.g., "get_spending_summary") */
    String getName();

    /** Human-readable description for Claude to understand when to use this tool */
    String getDescription();

    /** Parameter definitions for the tool */
    Map<String, ToolDefinition.ToolParameter> getParameters();

    /** Execute the tool with the given parameters. jwtToken is forwarded to Transaction Service. */
    McpToolResponse execute(McpToolCall call, String jwtToken);
}
