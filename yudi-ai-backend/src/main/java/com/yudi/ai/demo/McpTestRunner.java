package com.yudi.ai.demo;

import com.yudi.ai.mcp.AmapMcpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * MCP服务测试运行器
 * 在应用启动时自动测试MCP服务
 */
@Slf4j
@Component
public class McpTestRunner implements CommandLineRunner {

    private final AmapMcpService amapMcpService;

    public McpTestRunner(AmapMcpService amapMcpService) {
        this.amapMcpService = amapMcpService;
    }

    @Override
    public void run(String... args) {
        log.info("开始测试MCP服务...");
        
        // 检查MCP服务是否可用
        boolean isAvailable = amapMcpService.isMcpServiceAvailable();
        log.info("MCP服务状态: {}", isAvailable ? "可用" : "不可用");
        
        if (isAvailable) {
            // 获取所有工具
            var allTools = amapMcpService.getAllMcpTools();
            log.info("所有MCP工具: {}", allTools);
            
            // 获取高德地图工具
            var amapTools = amapMcpService.getAmapTools();
            log.info("高德地图工具: {}", amapTools);
            
            if (!amapTools.isEmpty()) {
                log.info("高德地图MCP服务集成成功！");
            } else {
                log.warn("未找到高德地图相关工具，请检查MCP服务器配置");
            }
        } else {
            log.error("MCP服务不可用，请检查配置和依赖");
        }
        
        log.info("MCP服务测试完成");
    }
}