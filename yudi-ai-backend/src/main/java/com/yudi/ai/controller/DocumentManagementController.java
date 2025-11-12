package com.yudi.ai.controller;

import com.yudi.ai.rag.PostgresVectorVectorStoreConfig;
import jakarta.annotation.Resource;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 文档管理控制器
 * 提供文档加载、重新加载等管理功能
 *
 * @author yudi
 */
@RestController
@RequestMapping("/api/document")
public class DocumentManagementController {

    @Resource
    private PostgresVectorVectorStoreConfig postgresVectorVectorStoreConfig;

    @Resource
    @Qualifier("pgVectorVectorStore")
    private VectorStore pgVectorVectorStore;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 强制重新加载所有文档(清空现有存在的所有数据)
     */
    @PostMapping("/reload")
    public Map<String, Object> forceReloadDocuments() {
        Map<String, Object> result = new HashMap<>();
        try {
            postgresVectorVectorStoreConfig.forceReloadDocuments(pgVectorVectorStore, jdbcTemplate);
            result.put("success", true);
            result.put("message", "文档强制重新加载完成");
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "文档重新加载失败: " + e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
        }
        return result;
    }

    /**
     * 智能增量加载文档
     * 支持新增文档和更新已修改的文档
     */
    @PostMapping("/incremental")
    public Map<String, Object> incrementalLoadDocuments() {
        Map<String, Object> result = new HashMap<>();
        try {
            postgresVectorVectorStoreConfig.incrementalLoadDocuments(pgVectorVectorStore, jdbcTemplate);
            result.put("success", true);
            result.put("message", "文档智能增量加载完成");
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "文档增量加载失败: " + e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
        }
        return result;
    }
    
    /**
     * 重载单个文档
     * 删除指定文档的所有向量数据并重新加载
     */
    @PostMapping("/reload/{filename}")
    public Map<String, Object> reloadSingleDocument(@PathVariable String filename) {
        Map<String, Object> result = new HashMap<>();
        try {
            postgresVectorVectorStoreConfig.reloadSingleDocument(pgVectorVectorStore, jdbcTemplate, filename);
            result.put("success", true);
            result.put("message", "文档 " + filename + " 重载完成");
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "文档重载失败: " + e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
        }
        return result;
    }
    
    /**
     * 清理无效向量数据
     * 删除文件系统中不存在的文档对应的向量
     */
    @PostMapping("/cleanup")
    public Map<String, Object> cleanupOrphanVectors() {
        Map<String, Object> result = new HashMap<>();
        try {
            postgresVectorVectorStoreConfig.cleanupOrphanVectors(jdbcTemplate);
            result.put("success", true);
            result.put("message", "无效向量数据清理完成");
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "清理失败: " + e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
        }
        return result;
    }

    /**
     * 清空所有文档
     */
    @DeleteMapping("/clear")
    public Map<String, Object> clearAllDocuments() {
        Map<String, Object> result = new HashMap<>();
        try {
            int deletedCount = jdbcTemplate.update("DELETE FROM public.vector_store");
            result.put("success", true);
            result.put("message", "已清空所有文档");
            result.put("deletedCount", deletedCount);
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "清空文档失败: " + e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
        }
        return result;
    }
}