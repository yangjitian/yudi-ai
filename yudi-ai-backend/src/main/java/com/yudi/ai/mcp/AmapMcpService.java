package com.yudi.ai.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 高德地图MCP服务
 * 提供地图相关的工具功能
 */
@Slf4j
@Service
public class AmapMcpService {

    @Autowired(required = false)
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * 获取高德地图相关的工具列表
     */
    public List<String> getAmapTools() {
        List<String> amapTools = new ArrayList<>();
        try {
            if (toolCallbackProvider == null) {
                log.warn("ToolCallbackProvider未配置，MCP服务可能未正确初始化");
                return amapTools;
            }
            
            ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
            for (ToolCallback callback : toolCallbacks) {
                String toolName = callback.getToolDefinition().name();
                if (toolName.toLowerCase().contains("amap") || 
                    toolName.toLowerCase().contains("map") ||
                    toolName.toLowerCase().contains("location") ||
                    toolName.toLowerCase().contains("geocoding")) {
                    amapTools.add(toolName);
                }
            }
        } catch (Exception e) {
            log.error("获取高德地图工具失败", e);
        }
        return amapTools;
    }

    /**
     * 执行高德地图工具
     */
    public Map<String, Object> executeAmapTool(String toolName, Map<String, Object> arguments) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (toolCallbackProvider == null) {
                result.put("success", false);
                result.put("message", "ToolCallbackProvider未配置，MCP服务可能未正确初始化");
                return result;
            }
            
            ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
            for (ToolCallback callback : toolCallbacks) {
                if (callback.getToolDefinition().name().equals(toolName)) {
                    // 将Map转换为JSON字符串
                    String jsonInput = convertMapToJson(arguments);
                    String toolResult = callback.call(jsonInput);
                    result.put("success", true);
                    result.put("data", toolResult);
                    result.put("toolName", toolName);
                    log.info("成功执行高德地图工具: {}", toolName);
                    return result;
                }
            }
            result.put("success", false);
            result.put("message", "未找到指定的工具: " + toolName);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "执行工具失败: " + e.getMessage());
            log.error("执行高德地图工具失败: {}", toolName, e);
        }
        return result;
    }

    /**
     * 获取所有可用的MCP工具
     */
    public List<String> getAllMcpTools() {
        List<String> allTools = new ArrayList<>();
        try {
            if (toolCallbackProvider == null) {
                log.warn("ToolCallbackProvider未配置，MCP服务可能未正确初始化");
                return allTools;
            }
            
            ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
            for (ToolCallback callback : toolCallbacks) {
                allTools.add(callback.getToolDefinition().name());
            }
        } catch (Exception e) {
            log.error("获取MCP工具列表失败", e);
        }
        return allTools;
    }

    /**
     * 检查MCP服务是否可用
     */
    public boolean isMcpServiceAvailable() {
        try {
            if (toolCallbackProvider == null) {
                return false;
            }
            
            ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
            return toolCallbacks.length > 0;
        } catch (Exception e) {
            log.error("MCP服务不可用", e);
            return false;
        }
    }

    /**
     * 将Map转换为JSON字符串
     */
    private String convertMapToJson(Map<String, Object> map) {
        try {
            // 简单的JSON转换
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    json.append(",");
                }
                json.append("\"").append(entry.getKey()).append("\":");
                if (entry.getValue() instanceof String) {
                    json.append("\"").append(entry.getValue()).append("\"");
                } else {
                    json.append(entry.getValue());
                }
                first = false;
            }
            json.append("}");
            return json.toString();
        } catch (Exception e) {
            log.error("转换Map为JSON失败", e);
            return "{}";
        }
    }
}