package com.yudi.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.yudi.ai.mapper")
public class YudiAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(YudiAiApplication.class, args);
    }

}
