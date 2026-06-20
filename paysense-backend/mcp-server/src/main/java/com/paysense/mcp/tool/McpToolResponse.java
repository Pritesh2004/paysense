package com.paysense.mcp.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from a tool execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolResponse {
    private boolean success;
    private Object data;
    private String error;

    public static McpToolResponse success(Object data) {
        return McpToolResponse.builder().success(true).data(data).build();
    }

    public static McpToolResponse failure(String error) {
        return McpToolResponse.builder().success(false).error(error).build();
    }
}
