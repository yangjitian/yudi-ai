package com.yudi.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 查询重写器 (添加内存缓存，避免重复查询重写)
 */
@Slf4j
@Component
public class QueryRewriter {

    private final QueryTransformer queryTransformer;

    // 简易实现 LRU 缓存
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final Queue<String> lruQueue = new ConcurrentLinkedQueue<>();

    private static final int MAX_SIZE = 1000;
    private static final int TRUNCATE_LEN = 2045;

    public QueryRewriter(ChatModel dashscopeChatModel) {
        this.queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(dashscopeChatModel))
                .build();
    }

    public String doQueryRewriter(String query) {
        if (query == null || query.isBlank()) return query;

        String key = query.trim().toLowerCase();

        // 命中缓存 → 更新 LRU 并返回
        String cached = cache.get(key);
        if (cached != null) {
            lruQueue.remove(key);
            lruQueue.offer(key);
            return cached;
        }

        // 调用大模型重写
        String rewritten;
        try {
            rewritten = queryTransformer.transform(new Query(query)).text();
            if (rewritten.length() > 2048) {
                rewritten = rewritten.substring(0, TRUNCATE_LEN) + "...";
            }
        } catch (Exception e) {
            log.warn("查询重写失败，降级使用原始查询: {}", query);
            return query;
        }

        // 缓存满时批量淘汰 10%
        if (cache.size() >= MAX_SIZE) {
            int evict = Math.max(1, MAX_SIZE / 10);
            for (int i = 0; i < evict; i++) {
                String oldest = lruQueue.poll();
                if (oldest != null) cache.remove(oldest);
            }
        }

        // 写入缓存并标记为最近使用
        cache.put(key, rewritten);
        lruQueue.offer(key);

        return rewritten;
    }
}