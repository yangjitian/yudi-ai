package com.yudi.yudiaiimagesearchmcpserver.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 图片搜索服务类
 */
@Slf4j
@Service
public class ImageSearchTool {
    @Value("${pexels.api.key}")
    private String apiKey;
    @Value("${pexels.api.url}")
    private String apiUrl;
    private static final int DEFAULT_PER_PAGE = 3;
    private static final int MAX_PER_PAGE = 7;

    @Tool(description = "Search and return image URLs from Pexels API (English query required).")
    public String searchImage(
            @ToolParam(description = "Search query in English (required).") String query,
            @ToolParam(description = "Number of images to return (optional, default 3, max 7)", required = false)
            Integer count) {

        if (StrUtil.isBlank(query)) {
            return "查询关键词不能为空";
        }

        int perPage = (count != null && count > 0) ? Math.min(count, MAX_PER_PAGE) : DEFAULT_PER_PAGE;

        try {
            // 请求头
            Map<String, String> headers = Map.of("Authorization", apiKey);
            // 参数
            Map<String, Object> params = Map.of("query", query, "per_page", perPage, "page", 1);

            // 发送请求
            String response = HttpUtil.createGet(apiUrl)
                    .addHeaders(headers)
                    .form(params)
                    .timeout(10000)
                    .execute()
                    .body();

            // 解析结果
            JSONObject json = JSONUtil.parseObj(response);
            JSONArray photos = json.getJSONArray("photos");
            if (photos == null || photos.isEmpty()) {
                return "[]";
            }

            // 提取图片地址
            List<String> urls = photos.stream()
                    .map(obj -> ((JSONObject) obj).getJSONObject("src"))
                    .filter(src -> src != null && StrUtil.isNotBlank(src.getStr("large")))
                    .map(src -> src.getStr("large"))
                    .collect(Collectors.toList());

            return JSONUtil.toJsonStr(urls);
        } catch (Exception e) {
            log.error("搜索图片出错", e);
            return "搜索图片时发生错误: " + e.getMessage();
        }
    }
}
