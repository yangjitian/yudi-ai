package com.yudi.ai.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * 基于 pgVector 实现的 rag 增强
 */
@Slf4j
@Configuration
public class PostgresVectorVectorStoreConfig {

    @Resource
    private CookDocumentLoader cookDocumentLoader;

    //是否在启动时自动检测文档变化
    private final boolean autoDetectChanges = Boolean.parseBoolean(
        System.getProperty("AUTO_DETECT_DOCUMENT_CHANGES", "true")
    );

    @Bean
    public VectorStore pgVectorVectorStore(@Qualifier("secondaryJdbcTemplate") JdbcTemplate jdbcTemplate,
                                          EmbeddingModel dashscopeEmbeddingModel) {
        // 确保pgvector扩展与目标表存在，避免首次启动时表不存在
        ensurePgVectorSchema(jdbcTemplate);
        VectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536)                    // Optional: defaults to model dimensions or 1536
                .distanceType(COSINE_DISTANCE)       // Optional: defaults to COSINE_DISTANCE
                .indexType(HNSW)                     // Optional: defaults to HNSW
                .initializeSchema(false)             // 已经手动初始化
                .schemaName("public")                // Optional: defaults to "public"
                .vectorTableName("vector_store")     // Optional: defaults to "vector_store"
                .maxDocumentBatchSize(10000)         // Optional: defaults to 10000
                .build();
        // 检查数据库中是否已有数据，避免重复插入
        if (isVectorStoreEmpty(jdbcTemplate)) {
            log.info("向量存储为空，初始化数据...");
            loadDocumentsToVectorStore(vectorStore);
        } else {
            log.info("向量存储中已有数据");
            if (autoDetectChanges) {
                log.info("启用自动检测，开始智能增量检测...");
                // 清理无效向量数据，保持数据一致
                cleanupOrphanVectors(jdbcTemplate);
                // 检测文档变化,自动执行智能增量加载
                incrementalLoadDocuments(vectorStore, jdbcTemplate);
                log.info("启动时文档检测完成！");
            } else {
                System.out.println("自动检测已禁用，请手动进行文档更新");
            }
        }
        return vectorStore;
    }

    private void ensurePgVectorSchema(JdbcTemplate jdbcTemplate) {
        // 安装扩展（若已存在忽略错误）并创建表（若不存在）
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        } catch (Exception ignored) {
        }
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS public.vector_store (" +
                        "id uuid PRIMARY KEY," +
                        "content text," +
                        "metadata jsonb," +
                        "embedding vector(1536)" +
                ")"
        );
    }

    /**
     * 检查向量存储表是否为空
     * @param jdbcTemplate JdbcTemplate实例
     * @return true如果表为空，false如果有数据
     */
    private boolean isVectorStoreEmpty(JdbcTemplate jdbcTemplate) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.vector_store", 
                    Integer.class
            );
            return count == null || count == 0;
        } catch (Exception e) {
            // 如果表不存在或查询失败，认为需要初始化
            return true;
        }
    }

    /**
     * 加载文档到向量存储
     * @param vectorStore 向量存储实例
     */
    private void loadDocumentsToVectorStore(VectorStore vectorStore) {
        // 加载文档
        List<Document> documents = cookDocumentLoader.loadMarkdowns();
        log.info("加载了 {} 个文档", documents.size());

        // 遵守嵌入模型每次最多25条输入限制，按批次写入
        final int maxBatchSize = 25;
        for (int startIndex = 0; startIndex < documents.size(); startIndex += maxBatchSize) {
            int endIndexExclusive = Math.min(startIndex + maxBatchSize, documents.size());
            List<Document> batch = documents.subList(startIndex, endIndexExclusive);
            vectorStore.add(batch);
            log.info("已添加第 {} 批文档，共 {} 个", startIndex / maxBatchSize + 1, batch.size());
        }
        log.info("数据初始化完成！");
    }

    /**
     * 清理无效的向量数据
     * 删除文件系统中不存在的文档对应的向量
     * @param jdbcTemplate JdbcTemplate实例
     */
    public void cleanupOrphanVectors(JdbcTemplate jdbcTemplate) {
        log.info("开始清理无效向量数据...");
        try {
            // 获取当前文件系统中的所有文档文件名
            List<Document> currentDocuments = cookDocumentLoader.loadMarkdowns();
            Set<String> currentFilenames = new HashSet<>();
            for (Document doc : currentDocuments) {
                String filename = (String) doc.getMetadata().get("filename");
                if (filename != null) {
                    currentFilenames.add(filename);
                }
            }

            // 获取数据库中所有文档的文件名
            List<Map<String, Object>> dbResults = jdbcTemplate.queryForList(
                    "SELECT DISTINCT metadata->>'filename' as filename FROM public.vector_store WHERE metadata->>'filename' IS NOT NULL"
            );

            Set<String> dbFilenames = new HashSet<>();
            for (Map<String, Object> row : dbResults) {
                String filename = (String) row.get("filename");
                if (filename != null) {
                    dbFilenames.add(filename);
                }
            }

            // 找出需要清理的文件
            Set<String> orphanFilenames = new HashSet<>(dbFilenames);
            orphanFilenames.removeAll(currentFilenames);

            if (orphanFilenames.isEmpty()) {
                return;
            }

            // 删除无效向量
            int totalDeleted = 0;
            for (String filename : orphanFilenames) {
                int deletedCount = jdbcTemplate.update(
                        "DELETE FROM public.vector_store WHERE metadata->>'filename' = ?",
                        filename
                );
                totalDeleted += deletedCount;
                log.info("已清理文档 {} 的 {} 个向量", filename, deletedCount);
            }
            log.info("清理完成，共删除 {} 个无效向量", totalDeleted);
        } catch (Exception e) {
            System.err.println("清理无效向量失败: " + e.getMessage());
        }
    }

    /**
     * 智能增量加载文档（支持新增和更新）
     * @param vectorStore 向量存储实例
     * @param jdbcTemplate JdbcTemplate实例
     */
    public void incrementalLoadDocuments(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        log.info("开始智能增量加载文档...");

        // 获取已存在的文档信息
        Map<String, Long> existingDocs = getExistingDocuments(jdbcTemplate);
        log.info("已存在 {} 个文档", existingDocs.size());

        // 加载所有文档
        List<Document> allDocuments = cookDocumentLoader.loadMarkdowns();
        List<Document> newDocuments = new ArrayList<>();
        List<Document> updatedDocuments = new ArrayList<>();

        // 分析文档变化
        for (Document document : allDocuments) {
            String documentId = (String) document.getMetadata().get("documentId");
            Long lastModified = (Long) document.getMetadata().get("lastModified");

            if (documentId != null && lastModified != null) {
                if (!existingDocs.containsKey(documentId)) {
                    // 新增文档
                    newDocuments.add(document);
                } else if (existingDocs.get(documentId) < lastModified) {
                    // 文档已更新
                    updatedDocuments.add(document);
                    // 删除旧的向量数据
                    deleteDocumentVectors(jdbcTemplate, documentId);
                }
            }
        }

        // 处理新增和更新的文档
        List<Document> documentsToAdd = new ArrayList<>();
        documentsToAdd.addAll(newDocuments);
        documentsToAdd.addAll(updatedDocuments);

        if (documentsToAdd.isEmpty()) {
            return;
        }

        log.info("发现 {} 个新增文档，{} 个更新文档", newDocuments.size(), updatedDocuments.size());

        // 批量添加文档
        final int maxBatchSize = 25;
        for (int startIndex = 0; startIndex < documentsToAdd.size(); startIndex += maxBatchSize) {
            int endIndexExclusive = Math.min(startIndex + maxBatchSize, documentsToAdd.size());
            List<Document> batch = documentsToAdd.subList(startIndex, endIndexExclusive);
            vectorStore.add(batch);
            log.info("已处理第 {} 批文档，共 {} 个", startIndex / maxBatchSize + 1, batch.size());
        }
        log.info("智能增量加载完成！");
    }

    /**
     * 强制重新加载所有文档（清空现有数据）
     * @param vectorStore 向量存储实例
     * @param jdbcTemplate JdbcTemplate实例
     */
    public void forceReloadDocuments(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        log.info("开始强制重新加载文档...");

        // 清空现有数据
        jdbcTemplate.execute("DELETE FROM public.vector_store");
        log.info("已清空现有数据");

        // 重新加载文档
        loadDocumentsToVectorStore(vectorStore);
        log.info("强制重新加载完成！");
    }

    /**
     * 删除指定文档的所有向量数据
     * @param jdbcTemplate JdbcTemplate实例
     * @param documentId 文档ID
     */
    private void deleteDocumentVectors(JdbcTemplate jdbcTemplate, String documentId) {
        try {
            int deletedCount = jdbcTemplate.update(
                "DELETE FROM public.vector_store WHERE metadata->>'documentId' = ?",
                documentId
            );
            log.info("已删除文档 {} 的 {} 个向量", documentId,deletedCount);
        } catch (Exception e) {
            System.err.println("删除文档向量失败: " + e.getMessage());
        }
    }

    /**
     * 重载单个文档
     * @param vectorStore 向量存储实例
     * @param jdbcTemplate JdbcTemplate实例
     * @param filename 文件名
     */
    public void reloadSingleDocument(VectorStore vectorStore, JdbcTemplate jdbcTemplate, String filename) {
        log.info("开始重载文档: {}", filename);

        try {
            // 删除该文档的所有向量数据
            int deletedCount = jdbcTemplate.update(
                "DELETE FROM public.vector_store WHERE metadata->>'filename' = ?",
                filename
            );
            log.info("已删除文档:{} 的 {} 个向量",filename,deletedCount);

            // 重新加载该文档
            List<Document> allDocuments = cookDocumentLoader.loadMarkdowns();
            List<Document> targetDocuments = new ArrayList<>();

            for (Document document : allDocuments) {
                String docFilename = (String) document.getMetadata().get("filename");
                if (filename.equals(docFilename)) {
                    targetDocuments.add(document);
                }
            }

            if (targetDocuments.isEmpty()) {
                log.info("未找到文档: " + filename);
                return;
            }

            // 添加文档
            vectorStore.add(targetDocuments);
            log.info("已重新加载文档: {}，共 {} 个分片", filename, targetDocuments.size());

        } catch (Exception e) {
            System.err.println("重载文档失败: " + e.getMessage());
        }
    }

    /**
     * 获取已存在的文档信息
     * @param jdbcTemplate JdbcTemplate实例
     * @return 已存在的文档信息映射 (documentId -> lastModified)
     */
    private Map<String, Long> getExistingDocuments(JdbcTemplate jdbcTemplate) {
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT DISTINCT metadata->>'documentId' as documentId, metadata->>'lastModified' as lastModified FROM public.vector_store WHERE metadata->>'documentId' IS NOT NULL"
            );
            
            Map<String, Long> existingDocs = new HashMap<>();
            for (Map<String, Object> row : results) {
                String documentId = (String) row.get("documentId");
                Object lastModifiedObj = row.get("lastModified");
                if (documentId != null && lastModifiedObj != null) {
                    try {
                        Long lastModified = Long.valueOf(lastModifiedObj.toString());
                        existingDocs.put(documentId, lastModified);
                    } catch (NumberFormatException e) {
                        System.err.println("解析lastModified失败: " + lastModifiedObj);
                    }
                }
            }
            return existingDocs;
        } catch (Exception e) {
            System.err.println("获取已存在文档信息失败: " + e.getMessage());
            return new HashMap<>();
        }
    }
}
