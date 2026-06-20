package com.paysense.mcp.controller;

import com.paysense.mcp.dto.ToolDefinition;
import com.paysense.mcp.registry.McpRegistry;
import com.paysense.mcp.security.JwtUserDetails;
import com.paysense.mcp.tool.McpTool;
import com.paysense.mcp.tool.McpToolCall;
import com.paysense.mcp.tool.McpToolResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP Tools Controller — tool discovery and direct tool execution.
 *
 * GET /mcp/tools — returns all tool definitions (public, for Claude discovery)
 * POST /mcp/tools/{toolName} — executes a specific tool (authenticated, for testing)
 */
@RestController
@RequestMapping("/mcp/tools")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class McpToolsController {

    private static final Logger log = LoggerFactory.getLogger(McpToolsController.class);
    private final McpRegistry mcpRegistry;

    /**
     * List all available MCP tools with their definitions.
     * Claude reads this to discover what tools are available.
     */
    @GetMapping
    public ResponseEntity<List<ToolDefinition>> listTools() {
        return ResponseEntity.ok(mcpRegistry.getToolDefinitions());
    }

    /**
     * Execute a specific tool directly (for testing and debugging).
     * Requires JWT authentication.
     */
    @PostMapping("/{toolName}")
    public ResponseEntity<McpToolResponse> executeTool(
            @PathVariable String toolName,
            @RequestBody Map<String, Object> parameters,
            @AuthenticationPrincipal JwtUserDetails principal) {

        log.info("Direct tool execution: tool={}, userId={}", toolName, principal.getId());

        return mcpRegistry.getTool(toolName)
                .map(tool -> {
                    McpToolCall call = McpToolCall.builder()
                            .toolName(toolName)
                            .parameters(parameters)
                            .build();

                    String jwtToken = extractJwtToken();
                    McpToolResponse response = tool.execute(call, jwtToken);
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private String extractJwtToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof String) {
            return (String) auth.getCredentials();
        }
        return "";
    }
}
