package com.paysense.mcp.registry;

import com.paysense.mcp.dto.ToolDefinition;
import com.paysense.mcp.tool.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of all MCP tools available for Claude to invoke.
 * Auto-discovers all McpTool beans at startup.
 * Claude reads GET /mcp/tools to know what tools are available.
 */
@Component
public class McpRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpRegistry.class);
    private final Map<String, McpTool> toolMap = new LinkedHashMap<>();

    public McpRegistry(List<McpTool> tools) {
        for (McpTool tool : tools) {
            toolMap.put(tool.getName(), tool);
            log.info("Registered MCP tool: {} — {}", tool.getName(), tool.getDescription());
        }
        log.info("MCP Registry initialized with {} tools", toolMap.size());
    }

    /**
     * Get all registered tool definitions (for Claude tool discovery).
     */
    public List<ToolDefinition> getToolDefinitions() {
        return toolMap.values().stream()
                .map(tool -> ToolDefinition.builder()
                        .name(tool.getName())
                        .description(tool.getDescription())
                        .inputSchema(tool.getParameters())
                        .build())
                .toList();
    }

    /**
     * Get a specific tool by name.
     */
    public Optional<McpTool> getTool(String name) {
        return Optional.ofNullable(toolMap.get(name));
    }

    /**
     * Get all tool names.
     */
    public List<String> getToolNames() {
        return List.copyOf(toolMap.keySet());
    }

    /**
     * Build Anthropic-compatible tool definitions for the Claude API request.
     * Format: [{name, description, input_schema: {type: "object", properties: {...}, required: [...]}}]
     */
    public List<Map<String, Object>> getClaudeToolDefinitions() {
        return toolMap.values().stream().map(tool -> {
            Map<String, Object> toolDef = new LinkedHashMap<>();
            toolDef.put("name", tool.getName());
            toolDef.put("description", tool.getDescription());

            // Build input_schema in JSON Schema format
            Map<String, Object> inputSchema = new LinkedHashMap<>();
            inputSchema.put("type", "object");

            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new java.util.ArrayList<>();

            tool.getParameters().forEach((paramName, param) -> {
                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", param.getType());
                prop.put("description", param.getDescription());
                properties.put(paramName, prop);
                if (param.isRequired()) {
                    required.add(paramName);
                }
            });

            inputSchema.put("properties", properties);
            inputSchema.put("required", required);
            toolDef.put("input_schema", inputSchema);

            return toolDef;
        }).toList();
    }

    /**
     * Build Gemini-compatible tool definitions for the Google Gemini API request.
     */
    public List<Map<String, Object>> getGeminiToolDefinitions() {
        List<Map<String, Object>> functionDeclarations = toolMap.values().stream().map(tool -> {
            Map<String, Object> toolDef = new LinkedHashMap<>();
            toolDef.put("name", tool.getName());
            toolDef.put("description", tool.getDescription());

            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("type", "OBJECT");

            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new java.util.ArrayList<>();

            tool.getParameters().forEach((paramName, param) -> {
                Map<String, Object> prop = new LinkedHashMap<>();
                // Gemini expects types in UPPERCASE (e.g., STRING, INTEGER, NUMBER, BOOLEAN)
                String type = param.getType().toUpperCase();
                if (type.equals("NUMBER")) type = "NUMBER"; // valid
                else if (type.equals("INT") || type.equals("INTEGER")) type = "INTEGER";
                else if (type.equals("BOOL") || type.equals("BOOLEAN")) type = "BOOLEAN";
                else type = "STRING";

                prop.put("type", type);
                prop.put("description", param.getDescription());
                properties.put(paramName, prop);
                if (param.isRequired()) {
                    required.add(paramName);
                }
            });

            parameters.put("properties", properties);
            if (!required.isEmpty()) {
                parameters.put("required", required);
            }
            toolDef.put("parameters", parameters);

            return toolDef;
        }).toList();

        Map<String, Object> toolsWrapper = new LinkedHashMap<>();
        toolsWrapper.put("functionDeclarations", functionDeclarations);
        return List.of(toolsWrapper);
    }
}
