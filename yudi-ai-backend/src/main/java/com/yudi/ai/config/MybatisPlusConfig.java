package com.yudi.ai.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * MyBatis Plus 自动填充处理器
 * 用于自动填充创建时间和更新时间
 */
@Configuration
public class MybatisPlusConfig {


    /**
     * 自动填充处理器 - 处理时区问题
     *
     * @return {@link MetaObjectHandler}
     */
    @Bean
    public MetaObjectHandler mateObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime date = LocalDateTime.now();
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, date);
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, date);
                this.strictInsertFill(metaObject, "conversationTime", LocalDateTime.class, date);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                LocalDateTime date = LocalDateTime.now();
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, date);
            }
        };
    }
}

