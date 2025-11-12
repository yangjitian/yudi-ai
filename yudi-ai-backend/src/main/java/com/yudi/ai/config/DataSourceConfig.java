package com.yudi.ai.config;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 数据源配置管理类
 * 统一管理MySQL和PostgreSQL两个数据源的配置
 */
@Configuration
public class DataSourceConfig {

    @Resource
    private DataSourceProperties dataSourceProperties;

    /**
     * 主数据源 - MySQL
     */
    @Bean(name = "primaryDataSource")
    @Primary
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create()
                .driverClassName(dataSourceProperties.getPrimary().getDriverClassName())
                .url(dataSourceProperties.getPrimary().getUrl())
                .username(dataSourceProperties.getPrimary().getUsername())
                .password(dataSourceProperties.getPrimary().getPassword())
                .build();
    }

    /**
     * 辅助数据源 - PostgreSQL
     */
    @Bean(name = "secondaryDataSource")
    public DataSource secondaryDataSource() {
        return DataSourceBuilder.create()
                .driverClassName(dataSourceProperties.getSecondary().getDriverClassName())
                .url(dataSourceProperties.getSecondary().getUrl())
                .username(dataSourceProperties.getSecondary().getUsername())
                .password(dataSourceProperties.getSecondary().getPassword())
                .build();
    }

    /**
     * 主数据源的JdbcTemplate
     */
    @Bean(name = "primaryJdbcTemplate")
    @Primary
    public JdbcTemplate primaryJdbcTemplate(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 辅助数据源的JdbcTemplate
     */
    @Bean(name = "secondaryJdbcTemplate")
    public JdbcTemplate secondaryJdbcTemplate(@Qualifier("secondaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 主数据源事务管理器
     */
    @Bean(name = "primaryTransactionManager")
    @Primary
    public PlatformTransactionManager primaryTransactionManager(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * 辅助数据源事务管理器
     */
    @Bean(name = "secondaryTransactionManager")
    public PlatformTransactionManager secondaryTransactionManager(@Qualifier("secondaryDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}