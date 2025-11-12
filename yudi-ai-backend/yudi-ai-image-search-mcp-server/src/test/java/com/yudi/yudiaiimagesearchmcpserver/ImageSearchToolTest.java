package com.yudi.yudiaiimagesearchmcpserver;

import com.yudi.yudiaiimagesearchmcpserver.tools.ImageSearchTool;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ImageSearchToolTest {

    @Resource
    private ImageSearchTool imageSearchTool;

    @Test
    void searchImage() {
        String result = imageSearchTool.searchImage("help me search some photos those about sea or sea animals",3);
        Assertions.assertNotNull(result);
        System.out.println(result);
    }
}
