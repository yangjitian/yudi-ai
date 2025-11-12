package com.yudi.ai.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 联网搜索工具类
 */
@Component
public class WebSearchTool {

    private final OkHttpClient client = new OkHttpClient();

    @Value("${search-api.base-url}")
    private String baseUrl;

    @Value("${search-api.key}")
    private String apiKey;

    @Value("${search-api.engine}")
    private String engine;

    @Value("${search-api.max-results}")
    private int maxResults;


    /**
     * 执行联网搜索
     * @param query 搜索问题
     * @return 返回结果
     */
    @Tool(description = "Search for information from Baidu Search Engine")
    public String search(String query) {
        if (StrUtil.isBlank(query)) {
            return "搜索关键词不能为空。";
        }
        try {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
            urlBuilder.addQueryParameter("engine", engine);
            urlBuilder.addQueryParameter("q", query);
            urlBuilder.addQueryParameter("api_key", apiKey);

            Request request = new Request.Builder()
                    .url(urlBuilder.build())
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return "请求失败，HTTP状态码：" + response.code();
                }

                String jsonStr = response.body().string();
                return parseJson(jsonStr);
            }
        } catch (IOException e) {
            return "网络请求异常：" + e.getMessage();
        } catch (Exception e) {
            return "搜索出错：" + e.getMessage();
        }
    }

    /**
     * 使用 Hutool JSON 解析结果
     */
    private String parseJson(String jsonStr) {
        if (StrUtil.isBlank(jsonStr)) {
            return "响应为空。";
        }

        JSONObject json = JSONUtil.parseObj(jsonStr);
        JSONArray results = json.getJSONArray("organic_results");
        if (results == null || results.isEmpty()) {
            return "未找到搜索结果。";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(results.size(), maxResults); i++) {
            JSONObject item = results.getJSONObject(i);
            sb.append("【结果 ").append(i + 1).append("】\n")
                    .append("标题：").append(item.getStr("title", "无")).append("\n")
                    .append("链接：").append(item.getStr("link", "无")).append("\n")
                    .append("摘要：").append(item.getStr("snippet", "无")).append("\n\n");
        }

        return sb.toString();
    }
}
