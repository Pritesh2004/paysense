package com.paysense.mcp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Tool definition returned by GET /mcp/tools — tells Claude what tools are available.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {
    private String name;
    private String description;
    private Map<String, ToolParameter> inputSchema;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolParameter {
        private String type;
        private String description;
        private boolean required;
    }
}
