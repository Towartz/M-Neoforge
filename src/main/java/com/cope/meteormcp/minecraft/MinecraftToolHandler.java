package com.cope.meteormcp.minecraft;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import java.util.Map;

public interface MinecraftToolHandler {
   List<Tool> getTools();
   CallToolResult execute(String toolName, Map<String, Object> arguments);
}
