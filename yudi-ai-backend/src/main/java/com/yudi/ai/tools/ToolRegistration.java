package com.yudi.ai.tools;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolRegistration {

    @Bean
    public Object[] allToolInstances(ApplicationContext context) {
        // 1. 手动 new 的工具类
        DocumentGenerationTool documentGenerationTool = new DocumentGenerationTool();
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        TerminateTool terminateTool = new TerminateTool();

        // 2. 从 Spring 容器获取 @Component 的工具类
        WebSearchTool webSearchTool = context.getBean(WebSearchTool.class);
        ResourceDownloadTool resourceDownloadTool = context.getBean(ResourceDownloadTool.class);
        TerminalTool terminalTool = context.getBean(TerminalTool.class);
        WeatherSearchTool weatherSearchTool = context.getBean(WeatherSearchTool.class);
        BaiduImageSearchTool baiduImageSearchTool = context.getBean(BaiduImageSearchTool.class);
        DateTimeTool dateTimeTool = context.getBean(DateTimeTool.class);

        // 3. 返回所有工具实例的数组
        return new Object[]{
                documentGenerationTool,
                fileOperationTool,
                webScrapingTool,
                terminateTool,
                terminalTool,
                webSearchTool,
                resourceDownloadTool,
                weatherSearchTool,
                baiduImageSearchTool,
                dateTimeTool
        };
    }
}
