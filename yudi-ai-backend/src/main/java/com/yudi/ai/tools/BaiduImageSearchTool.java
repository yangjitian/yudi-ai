package com.yudi.ai.tools;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 百度图片搜索工具类
 * <p>
 * 提供基于百度图片的搜索功能,返回高质量图片链接
 */
@Slf4j
@Component
public class BaiduImageSearchTool {

    @Value("${image-api.url}")
    private String url;

    @Value("${image-api.id}")
    private String id;

    @Value("${image-api.key}")
    private String key;

    private static final int DEFAULT_LIMIT = 1;
    private static final int MAX_LIMIT = 10;

    /**
     * 搜索百度图片主方法
     *
     * @param keywords 搜索关键词
     * @param limit    返回的图片数量（非必须）
     * @return JSON  格式的字符串结果
     */
    @Tool(description = "Search images from Baidu Image Search. Supports Chinese keywords. Returns high-quality image URLs.")
    public String searchBaiduImage(
            @ToolParam(description = "Search keywords") String keywords,
            @ToolParam(description = "Number of images to return (optional, default 1, max 10)", required = false) Integer limit) {

        if (StrUtil.isBlank(keywords)) {
            return "搜索关键词不能为空";
        }
        int imageLimit = (limit != null && limit > 0) ? Math.min(limit,MAX_LIMIT) : DEFAULT_LIMIT;

        try {
            Map<String, Object> params = MapUtil.builder(new HashMap<String, Object>())
                    .put("id", id)
                    .put("key", key)
                    .put("limit", imageLimit)
                    .put("words", keywords)
                    .build();

            String response = HttpUtil.get(url, params);
            return parseResponse(response, keywords);
        } catch (Exception e) {
            return "搜索图片时发生错误: " + e.getMessage();
        }
    }

    /**
     * 解析 API 响应
     *
     * @param response 原始 HTTP 响应字符串
     * @param keywords 搜索关键词（用于日志输出和提示）
     * @return JSON 字符串结果
     */
    private String parseResponse(String response, String keywords) {
        if (StrUtil.isBlank(response)) {
            return "API 响应为空";
        }

        try {
            // 使用 Hutool 的 JSONUtil 将字符串解析为 JSONObject
            JSONObject json = JSONUtil.parseObj(response);

            int code = json.getInt("code", -1);
            if (code != 200) {
                return "API 返回错误码: " + code;
            }

            // 获取图片结果数组字段
            JSONArray resArray = json.getJSONArray("res");
            if (CollUtil.isEmpty(resArray)) {
                return buildResponse(keywords, 0, 0, CollUtil.newArrayList(),
                        "未找到相关图片，请尝试其他关键词");
            }

            // 使用 Stream 流式处理 JSONArray 转为 List<String>
            List<String> imageUrls = resArray.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList());

            // 实际返回的图片数量
            int actualCount = imageUrls.size();
            // 从响应中获取总图片数量
            int totalCount = json.getInt("count", actualCount);

            // 调用构建结果方法返回 JSON
            return buildResponse(keywords, actualCount, totalCount, imageUrls, null);

        } catch (Exception e) {
            log.error("解析 API 响应失败", e);
            // 返回错误信息
            return "解析 API 响应失败: " + e.getMessage();
        }
    }

    /**
     * 构建返回结果 JSON
     *
     * @param keywords   搜索关键词
     * @param count      实际返回图片数量
     * @param totalCount API 提供的总图片数量
     * @param imageUrls  图片 URL 列表
     * @param message    额外提示信息（可为空）
     * @return 格式化好的 JSON 字符串
     */
    private String buildResponse(String keywords, int count, int totalCount,
                                 List<String> imageUrls, String message) {
        JSONObject result = JSONUtil.createObj()
                .set("success", count > 0)
                .set("keywords", keywords)
                .set("count", count)
                .set("totalCount", totalCount)
                .set("images", imageUrls);

        // 如果 message 不为空，添加进结果中
        if (StrUtil.isNotBlank(message)) {
            result.set("message", message);
        }

        // 如果存在图片，则生成详细描述（供用户或AI参考）
        if (CollUtil.isNotEmpty(imageUrls)) {
            // 构建每张图片的编号与链接信息
            String list = IntStream.range(0, imageUrls.size())
                    .mapToObj(i -> StrUtil.format("{}. {}", i + 1, imageUrls.get(i))) // 序号 + 链接
                    .collect(Collectors.joining("\n")); // 换行分隔

            // 使用 StrUtil.format 构造完整描述文本
            String desc = StrUtil.format(
                    "成功找到 {} 张关于「{}」的图片：\n{}",
                    count, keywords, list
            );
            // 将描述信息添加进 JSON
            result.set("description", desc);
        }
        // 最后将 JSON 对象转换为字符串并返回
        return JSONUtil.toJsonStr(result);
    }
}






