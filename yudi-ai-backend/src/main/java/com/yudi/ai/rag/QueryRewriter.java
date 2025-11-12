package com.yudi.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 查询重写器
 * 优化：添加内存缓存，避免重复查询重写
 */
@Slf4j
@Component
public class QueryRewriter {

    private final QueryTransformer queryTransformer;
    
    // 简单的内存缓存，使用ConcurrentHashMap保证线程安全
    // Key: 原始查询, Value: 重写后的查询
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    
    // 最大缓存大小，避免内存溢出
    private static final int MAX_CACHE_SIZE = 1000;

    public QueryRewriter(ChatModel dashscopeChatModel){
        ChatClient.Builder builder = ChatClient.builder(dashscopeChatModel);
        queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder)
                .build();
    }

    public String doQueryRewriter(String prompt){
        // 检查缓存
        String cachedResult = cache.get(prompt);
        if (cachedResult != null) {
            log.debug("查询重写缓存命中: {}", prompt);
            return cachedResult;
        }
        
        // 执行查询重写
        Query query = new Query(prompt);
        Query transformedQuery = queryTransformer.transform(query);
        
        // 限制返回的查询长度，确保不超过DashScope模型的2048字符限制
        String rewrittenText = transformedQuery.text();
        if (rewrittenText.length() > 2048) {
            rewrittenText = rewrittenText.substring(0, 2045) + "...";
        }
        
        // 存入缓存（如果缓存未满）
        if (cache.size() < MAX_CACHE_SIZE) {
            cache.put(prompt, rewrittenText);
        } else {
            // 缓存已满，清理一些旧条目（简单的清理策略：移除10%的条目）
            int removeCount = Math.max(1, MAX_CACHE_SIZE / 10);
            // 使用keySet的迭代器，安全地移除条目
            int removed = 0;
            for (String key : cache.keySet()) {
                if (removed >= removeCount) {
                    break;
                }
                cache.remove(key);
                removed++;
            }
            cache.put(prompt, rewrittenText);
            log.debug("查询重写缓存已满，清理了 {} 个条目", removed);
        }
        
        return rewrittenText;
    }
}
