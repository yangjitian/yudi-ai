package com.yudi.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据源配置属性类
 * 用于支持自定义数据源配置属性
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "spring.datasource")
public class DataSourceProperties {

    private DataSourceConfig primary = new DataSourceConfig();
    private DataSourceConfig secondary = new DataSourceConfig();

    /**
     * 数据源配置内部类
     */
    @Setter
    @Getter
    public static class DataSourceConfig {
        private String driverClassName;
        private String url;
        private String username;
        private String password;

    }
}