package com.yudi.ai.demo.invoke;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * Http 调用 AI
 */
public class HttpAiInvoke {
    public static void main(String[] args) {

        String dashscopeApiKey = TestApiKey.API_KEY;
        
        // Create JSON payload
        JSONObject payload = new JSONObject();
        JSONObject input = new JSONObject();
        JSONObject parameters = new JSONObject();
        
        // 设置消息组
        JSONObject systemMessage = new JSONObject();
        systemMessage.putOpt("role", "system");
        systemMessage.putOpt("content", "You are a helpful assistant.");
        
        JSONObject userMessage = new JSONObject();
        userMessage.putOpt("role", "user");
        userMessage.putOpt("content", "你是谁？");
        
        input.putOpt("messages", JSONUtil.createArray()
                .put(systemMessage)
                .put(userMessage));
        
        parameters.putOpt("result_format", "message");
        
        payload.putOpt("model", "qwen-plus");
        payload.putOpt("input", input);
        payload.putOpt("parameters", parameters);
        
        // 构造http请求
        try {
            HttpResponse response = HttpRequest.post("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation")
                    .header("Authorization", "Bearer " + dashscopeApiKey)
                    .header("Content-Type", "application/json")
                    .body(payload.toString())
                    .execute();
            
            // 输出响应
            System.out.println("Response Status: " + response.getStatus());
            System.out.println("Response Body: " + response.body());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}